# SimplePdfReader

Trình đọc PDF đơn giản cho Android 15/16 (Kotlin + Jetpack Compose, dùng PdfRenderer có sẵn của hệ thống).

Tính năng: giao diện Material 3 (màu động, sáng/tối), cuộn dọc, chụm 2 ngón để zoom,
nhảy tới trang bất kỳ (bấm vào "3 / 120"), tìm chữ có tô sáng và chuyển giữa các kết quả,
mở PDF từ app khác.

## Cách 1: Android Studio
1. Mở thư mục này bằng Android Studio bản mới, đợi Gradle sync.
2. Build > Build Bundle(s) / APK(s) > Build APK(s).
3. File nằm ở `app/build/outputs/apk/debug/app-debug.apk`, chép sang điện thoại và cài.

## Cách 2: GitHub (không cần cài gì trên máy tính)
1. Tạo repo mới trên github.com, upload toàn bộ nội dung thư mục này (nhớ thư mục `.github`).
2. Vào tab Actions > "Build APK" > Run workflow (hoặc tự chạy khi push lên nhánh `main`).
3. Chạy xong (khoảng 3–5 phút), mở lần chạy đó, tải artifact `PdfReader-apk` ở cuối trang.
4. Giải nén ra `app-debug.apk`, cài lên điện thoại (cho phép "cài từ nguồn không xác định" nếu máy hỏi).
