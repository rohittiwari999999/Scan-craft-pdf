# Privacy Policy for ScanCraft PDF

**Last updated:** October 6, 2026

ScanCraft PDF ("we", "our", or "the app") is committed to protecting your privacy. This Privacy Policy explains how our mobile application handles user data.

## 1. Zero Cloud Data Collection
ScanCraft PDF is an offline-first application:
- We do **not** collect, store, sell, or transmit any of your personal information, scanned documents, images, or metadata to external servers or third parties.
- All image processing, edge cropping, filter application, and PDF generation are performed locally on your device hardware.

## 2. Permissions Used
- **Camera (`android.permission.CAMERA`):** Used solely when you tap the "Scan" button to capture real-time photos of documents for scanning and PDF conversion. No camera stream or photo is sent to any external server.
- **Photos / Media Access:** Used via Android's native system Photo Picker (`ActivityResultContracts.PickVisualMedia`) without requiring broad storage permissions, enabling you to import images you select.
- **Local Storage (`MediaStore` & App Files):** Used to save generated PDF documents on your device so you can view, manage, or share them.

## 3. Third-Party Sharing (WhatsApp & Share Chooser)
When you choose to share a document via WhatsApp or the Android system share sheet, the generated PDF file is passed strictly at your direction through Android's secure `FileProvider` mechanism to the application you choose. We have no access to your chats or recipient lists.

## 4. Children's Privacy
The app does not collect personal data from anyone, including children under 13.

## 5. Contact Us
If you have any questions or suggestions regarding this Privacy Policy, please contact the developer via the support link on our Google Play Store page.
