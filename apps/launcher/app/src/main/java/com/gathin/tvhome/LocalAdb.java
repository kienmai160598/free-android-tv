package com.gathin.tvhome;

import android.content.Context;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * A small adb client for this TV's own adbd on 127.0.0.1:5555, for what Android 14 keeps from a normal
 * app: seeing other apps' processes and force-stopping them. The launcher signs in with its own RSA key
 * (files/adbkey), so the first time the TV asks on screen to allow it, as it does for a computer. One
 * shell command per connection ({@link Session}), blocking: call it off the main thread. Needs adb over the network on
 * port 5555 (adb tcpip 5555, which lasts until the TV restarts).
 */
final class LocalAdb {

    /** adbd is not listening on the port: adb over the network is off. */
    static final class OffException extends IOException {
        OffException(Throwable cause) {
            super("adb is not listening on port " + PORT, cause);
        }
    }

    /** The TV showed its allow prompt and nobody accepted it in time, or it does not know the key and may not ask. */
    static final class NotAllowedException extends IOException {
        NotAllowedException() {
            super("the TV did not allow this key");
        }
    }

    static final int PORT = 5555;
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f,
            CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int VERSION = 0x01000001, MAX_DATA = 256 * 1024;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final int CONNECT_MS = 2000, REPLY_MS = 10_000;
    /** How long the allow prompt stays answerable. */
    static final int PROMPT_MS = 60_000;
    private static final int KEY_BITS = 2048;
    /** PKCS#1 v1.5 DigestInfo for SHA-1: adbd's token is signed as if it were a SHA-1 digest. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14};

    private final Context context;

    LocalAdb(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Runs a shell command and returns what it printed (stdout and stderr together). */
    String shell(String command) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Session session = new Session()) {
            session.run(command, true, output::write);
        }
        return output.toString("UTF-8");
    }

    /** What a command prints, chunk by chunk as adbd sends it. */
    interface Output {
        void write(byte[] chunk) throws IOException;
    }

    /** One connection running one command; close() ends it from any thread, also while it waits for output. */
    final class Session implements Closeable {
        private final Socket socket = new Socket();

        /**
         * Runs command until it ends or the session closes. With mayPrompt false, a key the TV does not know
         * yet fails with NotAllowedException instead of putting the allow prompt on screen.
         */
        void run(String command, boolean mayPrompt, Output output) throws IOException {
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", PORT), CONNECT_MS);
            } catch (IOException e) {
                throw new OffException(e);
            }
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(REPLY_MS);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            signIn(socket, in, out, mayPrompt);
            send(out, OPEN, 1, 0, ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8));
            int remote = 0;
            while (true) {
                Message m = read(in);
                if (m.command == OKAY) {
                    remote = m.arg0;
                } else if (m.command == WRTE) {
                    output.write(m.data);
                    send(out, OKAY, 1, m.arg0, new byte[0]);
                } else if (m.command == CLSE) {
                    if (remote != 0) send(out, CLSE, 1, remote, new byte[0]);
                    return;
                }
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing is all that was asked.
            }
        }
    }

    private void signIn(Socket s, DataInputStream in, OutputStream out, boolean mayPrompt) throws IOException {
        KeyPair key = key();
        send(out, CNXN, VERSION, MAX_DATA, "host::\0".getBytes(StandardCharsets.US_ASCII));
        boolean signed = false;
        while (true) {
            Message m = read(in);
            if (m.command == CNXN) return;
            if (m.command != AUTH || m.arg0 != AUTH_TOKEN) throw new IOException("unexpected adb reply");
            if (!signed) {
                send(out, AUTH, AUTH_SIGNATURE, 0, sign(key.getPrivate(), m.data));
                signed = true;
            } else {
                if (!mayPrompt) throw new NotAllowedException();
                // The TV does not know this key yet: offer it, which puts the allow prompt on screen.
                send(out, AUTH, AUTH_RSAPUBLICKEY, 0, publicKey((RSAPublicKey) key.getPublic()));
                s.setSoTimeout(PROMPT_MS);
                try {
                    Message reply = read(in);
                    if (reply.command != CNXN) throw new NotAllowedException();
                } catch (SocketTimeoutException | EOFException e) {
                    // Denied, or no answer before the prompt timed out.
                    throw new NotAllowedException();
                }
                s.setSoTimeout(REPLY_MS);
                return;
            }
        }
    }

    /** The launcher's key, made on first use and kept in its private files. */
    private KeyPair key() throws IOException {
        File priv = new File(context.getFilesDir(), "adbkey");
        File pub = new File(context.getFilesDir(), "adbkey.pub");
        try {
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            if (priv.isFile() && pub.isFile()) {
                return new KeyPair(rsa.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                        rsa.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(KEY_BITS);
            KeyPair pair = gen.generateKeyPair();
            write(priv, pair.getPrivate().getEncoded());
            write(pub, pair.getPublic().getEncoded());
            return pair;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    /** RSA over the PKCS#1 v1.5 padded token, the signature adbd checks with RSA_verify(NID_sha1). */
    static byte[] sign(PrivateKey key, byte[] token) throws IOException {
        int size = KEY_BITS / 8;
        byte[] block = new byte[size];
        int tail = SHA1_PREFIX.length + token.length;
        block[1] = 0x01;
        for (int i = 2; i < size - tail - 1; i++) block[i] = (byte) 0xff;
        System.arraycopy(SHA1_PREFIX, 0, block, size - tail, SHA1_PREFIX.length);
        System.arraycopy(token, 0, block, size - token.length, token.length);
        try {
            Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key);
            return c.doFinal(block);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /**
     * The public key in adb's own format: mincrypt's RSAPublicKey struct (word count, n0inv, modulus and
     * R^2 mod n as little-endian words, exponent), base64, then a name for the prompt.
     */
    static byte[] publicKey(RSAPublicKey key) {
        int words = KEY_BITS / 32;
        BigInteger n = key.getModulus();
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putLittleEndian(b, n, words * 4);
        putLittleEndian(b, rr, words * 4);
        b.putInt(key.getPublicExponent().intValue());
        String text = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tvhome@launcher\0";
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static void putLittleEndian(ByteBuffer b, BigInteger value, int length) {
        byte[] big = value.toByteArray();
        for (int i = 0; i < length; i++) {
            int at = big.length - 1 - i;
            b.put(at >= 0 ? big[at] : 0);
        }
    }

    private static final class Message {
        final int command, arg0, arg1;
        final byte[] data;

        Message(int command, int arg0, int arg1, byte[] data) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.data = data;
        }
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data) throws IOException {
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (byte x : data) sum += x & 0xff;
        h.putInt(command).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~command);
        out.write(h.array());
        out.write(data);
        out.flush();
    }

    private static Message read(DataInputStream in) throws IOException {
        byte[] header = new byte[24];
        in.readFully(header);
        ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int command = h.getInt(), arg0 = h.getInt(), arg1 = h.getInt(), length = h.getInt();
        if (length < 0 || length > MAX_DATA) throw new IOException("bad adb message");
        byte[] data = new byte[length];
        in.readFully(data);
        return new Message(command, arg0, arg1, data);
    }
}
