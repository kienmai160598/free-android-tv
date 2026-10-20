# TV in another home

Reach it over Tailscale, never by forwarding port 5555 on their router.

- **Tailscale on the TV** (Play Store; Amazon Appstore on Fire TV): the owner shares the machine with
  you from their tailnet, or it joins yours. Use its `100.x.y.z` address as `TV_HOST`.
- **Subnet router** when the TV cannot run it: a computer, Raspberry Pi or Apple TV in their home, in
  *your* tailnet, `tailscale set --advertise-routes=<their /24>`, route approved in the admin console.
  Use the TV's local address. Fails if their LAN range overlaps yours. Phones cannot route.
- On Linux clients: `tailscale set --accept-routes`. Tagged devices cannot reach shared machines.
- mDNS does not cross the tunnel: for Wireless-debugging-only TVs ask for the IP, connect port,
  pairing port and code, then `adb pair`, `adb connect`, `adb tcpip 5555`.
- Do not scan your own network for the TV. Anything that drops their Wi-Fi drops your access.
- Afterwards: debugging off on the TV, revoke the share or remove the device/routes.
