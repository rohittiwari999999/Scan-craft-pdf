# Google Play Console Release Package — ScanCraft PDF

All required deployment assets and build artifacts have been generated and packaged in this folder:

```
play_console_release_files/
├── ScanCraft_v1.0.aab           (Android App Bundle — Main file to upload in Google Play Console Production / Internal testing)
├── ScanCraft_v1.0.apk           (Universal Standalone APK — For direct device installation and testing)
├── icon_512x512.png             (High-Res App Icon for Play Store Store Listing: 512x512 PNG, 32-bit)
├── feature_graphic_1024x500.png (Feature Graphic Banner for Play Store Store Listing: 1024x500 PNG)
├── store_listing.txt            (Pre-written Play Store Title, Short Description, and Full Description)
├── privacy_policy.md            (Privacy Policy for Data Safety declaration)
└── app_details.json             (Technical metadata: Package ID, SDK versions, version code)
```

## Quick Steps for Play Console Upload:
1. **Create App**: In Google Play Console, click **Create app** and enter name: `ScanCraft PDF Scanner`.
2. **Upload Bundle**: Under **App releases** -> **Production** (or **Closed testing**), create a new release and upload `ScanCraft_v1.0.aab`.
3. **Store Listing Assets**:
   - In **Main store listing**, upload `icon_512x512.png` as the App Icon.
   - Upload `feature_graphic_1024x500.png` as the Feature Graphic.
   - Copy-paste the Title, Short description, and Full description from `store_listing.txt`.
4. **Privacy Policy**: Link to the provided `privacy_policy.md`.
5. **Direct APK Installation**: You can install `ScanCraft_v1.0.apk` directly onto any Android phone or tablet for testing.
