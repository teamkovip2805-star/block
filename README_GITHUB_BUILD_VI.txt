BLOCK GRID v2.5 - BUILD APK BANG GITHUB ACTIONS

Workflow da duoc them tai:
.github/workflows/android-apk.yml

CACH DUNG
1. Giai nen ZIP nay tren may tinh.
2. Tao mot repository moi tren https://github.com (Public hoac Private deu duoc).
3. Dua TOAN BO noi dung ben trong thu muc vua giai nen len repository, bao gom ca thu muc an .github.
   Cach dung Git tren may tinh, mo terminal tai thu muc vua giai nen:

   git init
   git add .
   git commit -m "Add Block Grid Android project"
   git branch -M main
   git remote add origin https://github.com/USERNAME/REPOSITORY.git
   git push -u origin main

   Thay USERNAME/REPOSITORY bang dia chi repository cua ban.

4. Mo repository tren GitHub > tab Actions.
5. Chon workflow "Build Android APK". Workflow cung tu chay khi push code.
   Neu can chay thu cong: chon Run workflow.
6. Khi build thanh cong (dau xanh), mo run vua hoan tat, keo xuong muc Artifacts.
7. Tai artifact "block-grid-debug-apk.zip", giai nen de lay app-debug.apk.
8. Chuyen app-debug.apk sang dien thoai Android va cai dat. Neu Android hoi, cho phep cai ung dung tu nguon nay.

THONG TIN BUILD
- Project duoc build: BlockGrid_AndroidStudio
- Java: 17
- Gradle: 8.9
- Android Gradle Plugin: 8.7.3
- Android compileSdk: 35
- Output: app-debug.apk (debug-signed, dung de cai thu)
- Artifact GitHub duoc luu 14 ngay.

LUU Y
- Day la APK debug, khong phai ban release ky bang khoa rieng de phat hanh len Google Play.
- Neu Actions bao loi, mo run > Build APK > xem log buoc that bai.
- Khong can dua keystore hoac mat khau ky APK vao repository.
