# Security Policy

## 🔒 Security & Privacy Guarantees

The **Alliance Broadband Auto-Login** application handles sensitive credentials (captive portal username and password) and takes security and privacy very seriously:

1. **Zero External Servers & No Telemetry**:
   - The application has **no tracking, no analytics, no third-party SDKs, and no ads**.
   - Your credentials and session data are **never sent to any external server, cloud provider, or third party**.

2. **Strictly Local Communication**:
   - Network requests are made exclusively to your configured local gateway / portal IP (e.g. `http://10.254.254.57/0/up/`).
   - Android's Network Security Configuration (`network_security_config.xml`) strictly forbids cleartext HTTP to any arbitrary internet destination, permitting it only for the captive portal IP.

3. **On-Device Storage**:
   - Saved credentials reside solely on the physical device in private application storage.

---

## 🛡️ Supported Versions

We provide security fixes and maintenance for the latest active release on `main`:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

---

## 🚨 Reporting a Vulnerability

If you discover a potential security vulnerability, please do **NOT** open a public issue.

Instead, please report it responsibly:
- **GitHub Private Vulnerability Advisory**: Go to the **Security** tab of this repository and click **Report a vulnerability**.
- **Direct Contact**: Reach out privately to the maintainer via GitHub profile contact.

We will acknowledge your report within 48 hours and coordinate a fix and release promptly.
