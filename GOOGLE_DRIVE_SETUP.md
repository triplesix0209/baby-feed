# Cấu hình Google Drive cho bản APK đang cài

Log ngày 14/10/2026 xác nhận: Google từ chối vì Android app chưa đăng ký OAuth2 đúng package và chứng chỉ ký.

Trong Google Cloud, chọn project hiện có:

1. **APIs & Services → Library → Google Drive API → Enable**.
2. **Google Auth Platform → Branding**: hoàn tất tên app, email hỗ trợ và email liên hệ.
3. **Audience**: chọn External nếu dùng Gmail cá nhân; ở chế độ Testing, thêm email của cả hai người vào **Test users**.
4. **Data Access → Add or remove scopes**: thêm `https://www.googleapis.com/auth/drive`. Đây là scope restricted, cần cấu hình consent phù hợp. Testing dùng cho các tài khoản được thêm; phát hành rộng cần xét duyệt của Google.
5. **Clients → Create client → Android**:

   - Name: `BabyFeed Android debug`
   - Package name: `triplesix.babyfeed`
   - SHA-1: `34:47:35:E9:50:42:EE:7F:AD:2A:AE:23:22:C4:3F:76:FC:29:56:D6`

6. Lưu. Nếu Android client đã có, đối chiếu package và SHA-1; không chỉ tạo Web client. Bản hiện tại dùng AuthorizationClient trực tiếp trên Android, không cần chép client secret vào app.
7. Sau khi cấu hình có hiệu lực, mở app → Đồng bộ → Google Drive → Liên kết, chọn tài khoản test và đồng ý cấp quyền.
8. App tạo thư mục `BabyFeed`. Mở Drive, chia sẻ thư mục cho tài khoản người còn lại với quyền Editor. Máy thứ hai bấm **Liên kết**; app tìm thư mục BabyFeed được chia sẻ cho tài khoản này.

SHA-1 trên được đọc từ khóa debug đang dùng để ký bản APK trên A53; đổi khóa ký/release cần đăng ký SHA-1 tương ứng. Không gửi khóa riêng hoặc client secret vào chat.

Nguồn: [Google Android authorization](https://developer.android.com/identity/authorization), [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).
