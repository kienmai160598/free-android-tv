package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Home's widget shelf: four cards in the open space between the header and the dock, one over each
 * dock column, so they line up with the apps below them.
 * <ul>
 *   <li>Weather: today's temperature and range ({@link Weather}); OK refreshes it.</li>
 *   <li>This TV: memory and storage in use; OK opens the TV's storage settings. (Android 14 lets an app
 *       end only its own background processes, so a launcher cannot free other apps' memory itself.)</li>
 *   <li>Now playing: the active media session; OK plays or pauses it. Needs the notification listener
 *       allowed ({@link NowPlayingListener}).</li>
 *   <li>Lunar calendar: today's lunar date, the year's can chi and the next holiday; OK steps through the
 *       holidays after it.</li>
 * </ul>
 * The owner picks which widgets show and in what order: an empty column holds an Add widget card (OK
 * lists the widgets not shown), and holding OK on a widget offers Move left, Move right and Remove.
 * The list is kept in the launcher's settings ({@link #ORDER_KEY}).
 * It scrolls away with the grid when focus goes below the dock. Nothing runs while Home is hidden, and
 * nothing on it animates except focus: the box this runs on is slow.
 */
final class WidgetShelf {

    static final String PREF_KEY = "widgets";
    /** The widgets on the shelf, left to right, comma separated. */
    static final String ORDER_KEY = "widget_order";
    static final String WEATHER = "weather", SYSTEM = "system", NOW_PLAYING = "now_playing", CALENDAR = "calendar";
    private static final String[] ALL = {WEATHER, SYSTEM, NOW_PLAYING, CALENDAR};
    private static final int MAX_CARDS = 4;

    /** What the shelf needs from Home: its menus, the storage page, and a say when the cards change. */
    interface Host {
        void showMenu(View anchor, List<MenuItem> items);

        void openStorage();

        /** The cards on the shelf changed: the header's Down target may have moved. */
        void slotsChanged();
    }

    static final class MenuItem {
        final int icon;
        final String title;
        final Runnable action;

        MenuItem(int icon, String title, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }
    /** A failed weather fetch is not retried sooner than this. */
    private static final long WEATHER_RETRY_MS = 5 * 60_000L;

    private final Activity activity;
    private final View shelf;
    private final WidgetCard[] cards;
    private final WidgetCard addCard;
    private final TextView addLabel;
    private final ImageView addIcon;
    /** The widgets shown, left to right (ids from {@link #ALL}). */
    private final List<String> order = new ArrayList<>();
    /** The cards on the shelf now, left to right: the widgets that fit, then Add widget if a column is free. */
    private final List<WidgetCard> slots = new ArrayList<>();
    private RecyclerView lastGrid;
    private int lastColumns = MAX_CARDS, lastDockTop;
    private final TextView weatherPlace, weatherTemp, weatherDetail;
    private final ShapeBadge weatherIcon;
    private final TextView ramValue, storageValue;
    private final M3Progress ramBar, storageBar;
    private final ImageView ramIcon, storageIcon;
    private final TextView nowApp, nowTitle, nowSubtitle;
    private final ImageView nowArt, nowToggle;
    private final TextView lunarLabel, lunarDate, holiday;
    private final TextView systemLabel, ramName, storageName;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float density;
    private final String city;
    private final Host host;
    private Palette palette = Palette.currentOrBaseline();
    private boolean enabled, started;

    private Weather.Report report;
    private boolean fetching;
    private long lastFailure;

    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private MediaController controller;
    private boolean noAccess;

    private int holidayStep;
    private IntConsumer downFromCard;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickSession;
    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            paintNowPlaying();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            paintNowPlaying();
        }

        @Override
        public void onSessionDestroyed() {
            pickSession(activeSessions());
        }
    };

    WidgetShelf(Activity activity, View shelf, boolean animations, boolean reducedMotion, Host host) {
        this.activity = activity;
        this.shelf = shelf;
        this.host = host;
        density = activity.getResources().getDisplayMetrics().density;
        city = activity.getString(R.string.weather_city).trim();
        cards = new WidgetCard[]{shelf.findViewById(R.id.widgetWeather), shelf.findViewById(R.id.widgetSystem),
                shelf.findViewById(R.id.widgetNowPlaying), shelf.findViewById(R.id.widgetCalendar)};
        weatherPlace = shelf.findViewById(R.id.weatherPlace);
        weatherTemp = shelf.findViewById(R.id.weatherTemp);
        weatherDetail = shelf.findViewById(R.id.weatherDetail);
        weatherIcon = shelf.findViewById(R.id.weatherIcon);
        ramValue = shelf.findViewById(R.id.ramValue);
        storageValue = shelf.findViewById(R.id.storageValue);
        ramBar = shelf.findViewById(R.id.ramBar);
        storageBar = shelf.findViewById(R.id.storageBar);
        ramIcon = shelf.findViewById(R.id.ramIcon);
        storageIcon = shelf.findViewById(R.id.storageIcon);
        nowApp = shelf.findViewById(R.id.nowPlayingApp);
        nowTitle = shelf.findViewById(R.id.nowPlayingTitle);
        nowSubtitle = shelf.findViewById(R.id.nowPlayingSubtitle);
        nowArt = shelf.findViewById(R.id.nowPlayingArt);
        nowToggle = shelf.findViewById(R.id.nowPlayingToggle);
        lunarLabel = shelf.findViewById(R.id.lunarLabel);
        lunarDate = shelf.findViewById(R.id.lunarDate);
        holiday = shelf.findViewById(R.id.holiday);
        systemLabel = shelf.findViewById(R.id.systemLabel);
        ramName = shelf.findViewById(R.id.ramName);
        storageName = shelf.findViewById(R.id.storageName);
        addCard = shelf.findViewById(R.id.widgetAdd);
        addLabel = shelf.findViewById(R.id.widgetAddLabel);
        addIcon = shelf.findViewById(R.id.widgetAddIcon);
        addCard.setGhost(true);
        loadOrder();

        nowArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Shapes.MEDIUM * density);
            }
        });
        nowArt.setClipToOutline(true);

        sessions = activity.getSystemService(MediaSessionManager.class);
        listener = new ComponentName(activity, NowPlayingListener.class);

        for (WidgetCard card : allCards()) {
            card.setMotion(animations, reducedMotion);
            card.setOnKeyListener((v, code, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int column = slots.indexOf(v);
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && downFromCard != null) {
                    downFromCard.accept(Math.max(0, column));
                    return true;
                }
                // The row's ends: focus must not wrap off the shelf sideways.
                return code == KeyEvent.KEYCODE_DPAD_LEFT && column == 0
                        || code == KeyEvent.KEYCODE_DPAD_RIGHT && column == slots.size() - 1;
            });
        }
        for (int i = 0; i < ALL.length; i++) {
            String id = ALL[i];
            cards[i].setOnLongClickListener(v -> {
                showEditMenu(id);
                return true;
            });
        }
        addCard.setOnClickListener(v -> showAddMenu());
        cards[0].setOnClickListener(v -> refreshWeather(true));
        cards[1].setOnClickListener(v -> host.openStorage());
        cards[2].setOnClickListener(v -> togglePlayback());
        cards[3].setOnClickListener(v -> {
            holidayStep++;
            paintCalendar();
        });
    }

    /** Down from the card over dock column i: the activity focuses the app below it. */
    void setOnDown(IntConsumer focusDock) {
        downFromCard = focusDock;
    }

    boolean enabled() {
        return enabled;
    }

    /** Shows or hides the shelf (Launcher settings, "widgets"); a hidden shelf reads nothing. */
    void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        shelf.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) stop();
    }

    /** Focus moves up from dock column i: onto the card over it. */
    boolean focusFromDock(int column) {
        int n = slots.size();
        if (!enabled || n == 0 || shelf.getAlpha() < 0.5f) return false;
        return slots.get(Math.max(0, Math.min(column, n - 1))).requestFocus();
    }

    /** The card header buttons should go down to: the last one, under them. */
    int cardUnderHeaderButtons() {
        return slots.isEmpty() ? addCard.getId() : slots.get(slots.size() - 1).getId();
    }

    private WidgetCard[] allCards() {
        return new WidgetCard[]{cards[0], cards[1], cards[2], cards[3], addCard};
    }

    private WidgetCard card(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(id)) return cards[i];
        return null;
    }

    private boolean has(String id) {
        return order.contains(id);
    }

    // ---- choosing widgets ------------------------------------------------------------------------

    private void loadOrder() {
        order.clear();
        String saved = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(ORDER_KEY, String.join(",", ALL));
        for (String id : saved.split(",")) {
            if (card(id.trim()) != null && !order.contains(id.trim())) order.add(id.trim());
        }
    }

    /** Saves a new list and shows it, focus on focusId's card, or on the card now at the same column. */
    private void setOrder(List<String> next, String focusId, int fallbackColumn) {
        order.clear();
        order.addAll(next);
        activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(ORDER_KEY, String.join(",", order)).apply();
        if (started) {
            // Start or stop what the added or removed widget reads.
            stop();
            start();
        }
        if (lastGrid != null) align(lastGrid, lastColumns, lastDockTop);
        host.slotsChanged();
        shelf.post(() -> {
            WidgetCard target = focusId != null ? card(focusId) : null;
            if (target == null || !slots.contains(target)) {
                target = slots.isEmpty() ? null : slots.get(Math.max(0, Math.min(fallbackColumn, slots.size() - 1)));
            }
            if (target != null) target.requestFocus();
        });
    }

    private void showAddMenu() {
        List<MenuItem> items = new ArrayList<>();
        for (String id : ALL) {
            if (has(id)) continue;
            items.add(new MenuItem(icon(id), activity.getString(name(id)), () -> {
                List<String> next = new ArrayList<>(order);
                next.add(id);
                setOrder(next, id, next.size() - 1);
            }));
        }
        if (!items.isEmpty()) host.showMenu(addCard, items);
    }

    private void showEditMenu(String id) {
        int at = order.indexOf(id);
        if (at < 0) return;
        List<MenuItem> items = new ArrayList<>();
        if (at > 0) items.add(new MenuItem(R.drawable.ic_move_left, activity.getString(R.string.widget_move_left), () -> move(id, -1)));
        if (at < Math.min(order.size(), slotsLimit()) - 1) {
            items.add(new MenuItem(R.drawable.ic_move_right, activity.getString(R.string.widget_move_right), () -> move(id, 1)));
        }
        items.add(new MenuItem(R.drawable.ic_remove, activity.getString(R.string.widget_remove), () -> {
            List<String> next = new ArrayList<>(order);
            next.remove(id);
            setOrder(next, null, at);
        }));
        host.showMenu(card(id), items);
    }

    private void move(String id, int by) {
        List<String> next = new ArrayList<>(order);
        int at = next.indexOf(id);
        Collections.swap(next, at, at + by);
        setOrder(next, id, at + by);
    }

    /** How many cards fit: one per dock column, at most four. */
    private int slotsLimit() {
        return Math.min(MAX_CARDS, lastColumns);
    }

    private static int name(String id) {
        switch (id) {
            case WEATHER: return R.string.weather;
            case SYSTEM: return R.string.widget_system;
            case NOW_PLAYING: return R.string.now_playing;
            default: return R.string.lunar_calendar;
        }
    }

    private static int icon(String id) {
        switch (id) {
            case WEATHER: return R.drawable.ic_w_partly_day;
            case SYSTEM: return R.drawable.ic_memory;
            case NOW_PLAYING: return R.drawable.ic_music;
            default: return R.drawable.ic_calendar;
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    void start() {
        if (!enabled || started) return;
        started = true;
        if (has(CALENDAR)) paintCalendar();
        if (has(SYSTEM)) paintSystem();
        if (has(WEATHER)) refreshWeather(false);
        if (has(NOW_PLAYING)) startSessions();
    }

    void stop() {
        if (!started) return;
        started = false;
        stopSessions();
    }

    /** Once a minute while Home shows. */
    void tick() {
        if (!started) return;
        if (noAccess && has(NOW_PLAYING)) {
            // Access may have been allowed (adb) since Home started.
            stopSessions();
            startSessions();
        }
        if (has(CALENDAR)) paintCalendar();
        if (has(SYSTEM)) paintSystem();
        if (has(WEATHER)) refreshWeather(false);
    }

    // ---- placement -------------------------------------------------------------------------------

    /**
     * Puts the shown cards over the dock's columns, left to right (each card's visible part exactly over
     * its tile, its focus room around it), and centres the shelf in the space above the dock. dockTop is
     * the dock's top in the shelf's parent, which also holds the grid. At most one card per column and
     * four in all; a free column gets the Add widget card.
     */
    void align(RecyclerView grid, int columns, int dockTop) {
        lastGrid = grid;
        lastColumns = columns;
        lastDockTop = dockTop;
        if (!enabled) return;
        slots.clear();
        for (String id : order) if (slots.size() < slotsLimit()) slots.add(card(id));
        if (slots.size() < slotsLimit() && order.size() < ALL.length) slots.add(addCard);
        for (WidgetCard c : allCards()) {
            int visibility = slots.contains(c) ? View.VISIBLE : View.GONE;
            if (c.getVisibility() != visibility) c.setVisibility(visibility);
        }
        ViewGroup parent = (ViewGroup) shelf.getParent();
        int inset = Math.round(WidgetCard.REACH_DP * density);
        Rect r = new Rect();
        for (int i = 0; i < slots.size(); i++) {
            WidgetCard card = slots.get(i);
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(i);
            View tile = vh == null ? null : vh.itemView.findViewById(R.id.card);
            if (tile == null || tile.getWidth() == 0) return;   // not laid out yet: the next layout aligns
            r.set(0, 0, tile.getWidth(), tile.getHeight());
            parent.offsetDescendantRectToMyCoords(tile, r);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            int left = r.left - inset - shelf.getLeft(), width = r.width() + 2 * inset;
            if (lp.leftMargin != left || lp.width != width) {
                lp.leftMargin = left;
                lp.width = width;
                card.setLayoutParams(lp);
            }
        }
        ViewGroup.MarginLayoutParams slp = (ViewGroup.MarginLayoutParams) shelf.getLayoutParams();
        int top = Math.max(0, (dockTop - slp.height) / 2);
        if (slp.topMargin != top) {
            slp.topMargin = top;
            shelf.setLayoutParams(slp);
        }
    }

    /** Follows the grid's scroll: the shelf rides up with the dock and fades as it leaves. */
    void onGridScrolled(RecyclerView grid) {
        if (!enabled) return;
        int offset = grid.computeVerticalScrollOffset();
        shelf.setTranslationY(-offset);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) shelf.getLayoutParams();
        float bottom = Math.max(1f, lp.topMargin + lp.height);
        shelf.setAlpha(Math.max(0f, Math.min(1f, 1f - offset / bottom)));
    }

    // ---- colour ----------------------------------------------------------------------------------

    void setPalette(Palette p) {
        palette = p;
        for (WidgetCard c : allCards()) c.setPalette(p);
        addLabel.setTextColor(p.onSurfaceVariant);
        addIcon.setImageTintList(ColorStateList.valueOf(p.onSurfaceVariant));
        for (TextView t : new TextView[]{weatherPlace, nowApp, lunarLabel, systemLabel}) t.setTextColor(p.onSurfaceVariant);
        for (TextView t : new TextView[]{weatherTemp, lunarDate, nowTitle, ramName, storageName}) t.setTextColor(p.onSurface);
        for (TextView t : new TextView[]{weatherDetail, nowSubtitle, holiday, ramValue, storageValue}) t.setTextColor(p.onSurfaceVariant);
        ramIcon.setImageTintList(ColorStateList.valueOf(p.primary));
        storageIcon.setImageTintList(ColorStateList.valueOf(p.primary));
        ramBar.setPalette(p);
        storageBar.setPalette(p);
        weatherIcon.setPalette(p);
        nowToggle.setImageTintList(ColorStateList.valueOf(p.primary));
        paintNowPlaying();
    }

    // ---- weather ---------------------------------------------------------------------------------

    private void refreshWeather(boolean force) {
        if (report == null) report = Weather.cached(activity);
        paintWeather();
        long now = System.currentTimeMillis();
        if (fetching) return;
        if (!force && report != null && !report.stale(now)) return;
        if (!force && now - lastFailure < WEATHER_RETRY_MS) return;
        fetching = true;
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            Weather.Report fresh;
            try {
                fresh = Weather.fetch(app, city);
            } catch (Exception e) {
                fresh = null;
            }
            Weather.Report result = fresh;
            main.post(() -> {
                fetching = false;
                if (result != null) report = result;
                else lastFailure = System.currentTimeMillis();
                paintWeather();
            });
        }, "weather").start();
    }

    private void paintWeather() {
        if (report == null) {
            weatherPlace.setText(R.string.weather);
            weatherTemp.setText("–");
            weatherDetail.setText(lastFailure > 0 ? activity.getString(R.string.weather_offline) : "");
            weatherIcon.setIcon(activity.getDrawable(R.drawable.ic_w_cloud));
            return;
        }
        Weather.Sky sky = Weather.sky(report.code);
        weatherPlace.setText(report.place.isEmpty() ? activity.getString(R.string.weather) : report.place);
        weatherTemp.setText(Math.round(report.temp) + "°");
        weatherDetail.setText(activity.getString(R.string.weather_range, activity.getString(Weather.label(sky)),
                Math.round(report.high), Math.round(report.low)));
        weatherIcon.setIcon(activity.getDrawable(Weather.icon(sky, report.day)));
    }

    // ---- this TV ---------------------------------------------------------------------------------

    private void paintSystem() {
        ActivityManager am = activity.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        ramValue.setText(usedOf(used, mi.totalMem));
        ramBar.setProgress(mi.totalMem > 0 ? used / (float) mi.totalMem : 0f);
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), usedDisk = total - fs.getAvailableBytes();
            storageValue.setText(usedOf(usedDisk, total));
            storageBar.setProgress(total > 0 ? usedDisk / (float) total : 0f);
        } catch (IllegalArgumentException e) {
            storageValue.setText("");
        }
    }

    /** "1.4 / 2.0 GB": the unit once, when both sizes share it, so the meter's name keeps its room. */
    private String usedOf(long used, long total) {
        String u = size(used), t = size(total);
        int cut = lastSpace(t);
        if (cut > 0 && lastSpace(u) > 0 && u.substring(lastSpace(u)).equals(t.substring(cut))) u = u.substring(0, lastSpace(u));
        return activity.getString(R.string.widget_used_of, u, t);
    }

    private static int lastSpace(String s) {
        for (int i = s.length() - 1; i >= 0; i--) if (Character.isSpaceChar(s.charAt(i))) return i;
        return -1;
    }

    private String size(long bytes) {
        return Formatter.formatShortFileSize(activity, bytes);
    }

    // ---- now playing -----------------------------------------------------------------------------

    private void startSessions() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main);
            noAccess = false;
            pickSession(sessions.getActiveSessions(listener));
        } catch (SecurityException e) {
            noAccess = true;
            paintNowPlaying();
        }
    }

    private void stopSessions() {
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        } catch (RuntimeException ignored) {
        }
        if (controller != null) controller.unregisterCallback(controllerCallback);
        controller = null;
    }

    private List<MediaController> activeSessions() {
        try {
            return sessions.getActiveSessions(listener);
        } catch (SecurityException e) {
            return null;
        }
    }

    /** The playing session if there is one, else the most recent; null for none. */
    private void pickSession(List<MediaController> list) {
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    pick = c;
                    break;
                }
            }
            if (pick == null && !list.isEmpty()) pick = list.get(0);
        }
        if (controller != null && pick != null && controller.getSessionToken().equals(pick.getSessionToken())) {
            paintNowPlaying();
            return;
        }
        if (controller != null) controller.unregisterCallback(controllerCallback);
        controller = pick;
        if (controller != null) controller.registerCallback(controllerCallback, main);
        paintNowPlaying();
    }

    private boolean playing() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void togglePlayback() {
        if (controller == null) return;
        if (playing()) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    private void paintNowPlaying() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (controller == null || m == null) {
            nowApp.setText(R.string.now_playing);
            nowTitle.setText(noAccess ? R.string.now_playing_permission : R.string.now_playing_idle);
            nowTitle.setTextColor(palette.onSurfaceVariant);
            nowSubtitle.setText("");
            nowToggle.setVisibility(View.GONE);
            placeholderArt();
            return;
        }
        nowApp.setText(appLabel(controller.getPackageName()));
        nowTitle.setTextColor(palette.onSurface);
        nowTitle.setText(first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), m.getString(MediaMetadata.METADATA_KEY_TITLE)));
        nowSubtitle.setText(first(m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE), m.getString(MediaMetadata.METADATA_KEY_ARTIST)));
        nowToggle.setVisibility(View.VISIBLE);
        nowToggle.setImageResource(playing() ? R.drawable.ic_pause : R.drawable.ic_play);
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (art != null) {
            nowArt.setImageTintList(null);
            nowArt.setBackgroundColor(0);
            nowArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            nowArt.setImageBitmap(art);
        } else {
            placeholderArt();
        }
    }

    private void placeholderArt() {
        nowArt.setImageResource(R.drawable.ic_music);
        nowArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        nowArt.setImageTintList(ColorStateList.valueOf(palette.onSecondaryContainer));
        nowArt.setBackgroundColor(palette.secondaryContainer);
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return activity.getString(R.string.now_playing);
        }
    }

    private static String first(String a, String b) {
        return a != null && !a.isEmpty() ? a : b != null ? b : "";
    }

    // ---- lunar calendar --------------------------------------------------------------------------

    private void paintCalendar() {
        LocalDate today = LocalDate.now();
        LunarCalendar.Date l = LunarCalendar.toLunar(today);
        lunarLabel.setText(activity.getString(R.string.lunar_year, LunarCalendar.canChi(l.year)));
        String date = l.day + "/" + l.month;
        lunarDate.setText(l.leap ? activity.getString(R.string.lunar_leap, date) : date);
        List<LunarCalendar.Upcoming> upcoming = LunarCalendar.upcoming(today);
        if (upcoming.isEmpty()) {
            holiday.setText("");
            return;
        }
        LunarCalendar.Upcoming u = upcoming.get(Math.floorMod(holidayStep, upcoming.size()));
        String name = activity.getString(holidayName(u.holiday));
        holiday.setText(u.days == 0 ? activity.getString(R.string.holiday_today, name)
                : u.days == 1 ? activity.getString(R.string.holiday_tomorrow, name)
                : activity.getString(R.string.holiday_in_days, name, (int) u.days));
    }

    private static int holidayName(LunarCalendar.Holiday h) {
        switch (h) {
            case NEW_YEAR: return R.string.holiday_new_year;
            case TET: return R.string.holiday_tet;
            case HUNG_KINGS: return R.string.holiday_hung_kings;
            case REUNIFICATION: return R.string.holiday_reunification;
            case LABOUR: return R.string.holiday_labour;
            case MID_AUTUMN: return R.string.holiday_mid_autumn;
            case NATIONAL_DAY: return R.string.holiday_national_day;
            default: return R.string.holiday_christmas;
        }
    }
}
