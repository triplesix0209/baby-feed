# Sữa cho bé — Android

App native Java, Android 8 trở lên, giao diện Tiếng Việt / English. Không cần đăng nhập để dùng lịch nhắc và lịch sử trên máy.

## Sử dụng

1. Bật thông báo và quyền nhắc đúng giờ trong app.
2. Đặt ngày, giờ, lượng ml cho cữ đầu tiên.
3. Khi thực sự pha sữa, bấm **Đã pha sữa** và xác nhận lượng thực tế. Sau 30 phút app hỏi bé đã uống xong chưa. Nếu chưa, có thể nhắc lại sau 10 phút.
4. Ghi lượng còn lại. App tính lượng uống = lượng pha − lượng còn lại, lưu thời gian. Cữ tiếp theo được đặt theo khoảng cách nhập dạng `1h30p`, `2h` hoặc `45p`, tính từ lúc xác nhận uống xong và lượng ml bố mẹ chọn.
5. Tab **Cữ sữa** hiển thị lịch tiếp theo và tổng hôm nay. Tab **Báo cáo** cho phép chọn ngày để xem tổng lượng uống, lượng còn lại, trung bình và lịch sử từng cữ. Tab **Thiết Lập** chứa tùy chọn Tiếng Việt / English, phiên bản ứng dụng và kết nối Drive. Ngôn ngữ được lưu riêng trên từng máy. Các giá trị mặc định chỉ là giá trị nhập ban đầu, không phải khuyến nghị cho trẻ.
6. Popup **Ghi nhận** có hai tab **Sữa** và **Vitamin**, dùng chung ngày và giờ đã chọn. Tab Sữa nhập lượng đã uống và lượng đã bỏ của cữ trước đó; lượng đã pha được tính bằng tổng hai lượng này, không tạo hoặc thay đổi lịch nhắc hiện tại. **Xóa** trong Báo cáo bật chọn nhiều cữ; **Xác nhận** mới xóa, **Bỏ qua** thoát mà không xóa. Bản ghi xóa được đồng bộ sang máy còn lại; cả hai máy cần dùng bản 1.3 trở lên. Xóa lịch sử không hủy lịch nhắc hiện tại; dùng **Hủy lịch nhắc** ở trang chính để hủy cả đợt đang ghi nhận.
7. Khoảng cách `0p` đặt cữ tiếp theo ngay, `1p` đặt sau một phút. Không có giới hạn tối thiểu 15 phút.
8. Báo thức dùng kênh **Báo thức pha sữa**, âm thanh báo thức mặc định, rung, các nút Tắt và Nhắc lại 10 phút. Âm thanh dừng khi tắt/mở ghi nhận hoặc sau 60 giây. Android 14 trở lên cần cấp quyền **Full-screen notifications** qua nút trong app; khi khóa màn hình có thể hiện trang báo thức, khi đang dùng điện thoại Android có thể hiện banner lớn. Kích thước banner do Android/Samsung quyết định. Kiểm tra âm lượng Alarm và quyền thông báo nếu không nghe âm thanh. Không tự bỏ qua Do Not Disturb.

Thiết Lập gồm **Hiển thị**, **Thông báo**, **Widget**, **Đồng bộ**, **Gợi ý**, **Thông tin**. Trong Hiển thị, chạm **Ngôn ngữ** để đổi Việt/Anh hoặc **Giao diện** để chọn Sáng/Tối/Hệ thống (mặc định). Chế độ Hệ thống theo giao diện của điện thoại; lựa chọn ngôn ngữ và giao diện chỉ lưu trên máy hiện tại. Chạm tên provider để liên kết, đồng bộ, ngắt liên kết hoặc xem thông tin thư mục. Mở **Gợi ý → Cả nhà cùng chăm bé** để đọc hướng dẫn thiết lập gia đình. Phiên bản app nằm trong Thông tin.

**Thông báo → Giờ ngủ**: bật/tắt và chọn giờ bắt đầu, kết thúc (mặc định tắt, 22:00–06:00). Khung giờ lặp hằng ngày theo giờ địa phương trên điện thoại, hỗ trợ qua nửa đêm; bắt đầu được tính trong giờ ngủ, kết thúc không được tính. Hai giờ phải khác nhau. Khi bật, mọi nhắc pha sữa, nhắc ghi nhận và nhắc lại đến trong giờ ngủ đều được bỏ qua hoàn toàn, không thông báo/âm thanh/rung/màn hình báo thức và không phát bù khi hết giờ ngủ. Lịch và lịch sử vẫn giữ nguyên. Bật hoặc đổi giờ ngủ khi đang trong khung giờ cũng tắt thông báo đang phát. Cài đặt lưu riêng trên từng máy; gia đình cần bật trên từng thiết bị muốn giữ yên lặng.

Widget: mở **Thiết Lập → Widget → Cài đặt widget**, hoặc nhấn giữ widget trên màn hình chính và chọn cài đặt (nếu launcher hỗ trợ). Mỗi widget lưu riêng giao diện Sáng/Tối/Hệ thống (theo điện thoại, độc lập với giao diện app), chu kỳ refresh chọn bằng badge 1/5/10 phút (mặc định 5 phút) và độ trong suốt nền từ 0–100% theo từng mức 10%. Mỗi thay đổi được lưu và áp dụng ngay lên widget; không cần bấm Lưu. Bấm Back để rời màn hình. Độ trong suốt chỉ áp dụng cho nền, giữ rõ chữ và nút thao tác. Chu kỳ refresh dùng quyền nhắc đúng giờ đã cấp; Android có thể trì hoãn khi ngủ hoặc khi chưa cấp quyền. Lịch nhắc cữ sữa vẫn hoạt động riêng.

## Vitamin K2D3

**Thiết Lập → Thông báo → Giờ uống Vitamin**: mặc định tắt. Khi bật, chọn một giờ để nhắc hằng ngày (giá trị nhập ban đầu 09:00). Cài đặt lưu riêng trên từng máy. Thông báo dùng kênh **Nhắc uống Vitamin**; chạm thông báo mở popup Ghi nhận ở tab Vitamin. Nếu ngày đó đã ghi nhận uống, hoặc giờ đã đặt nằm trong giờ ngủ, app bỏ qua và không nhắc bù. Khi Android gửi trễ, app kiểm tra cả giờ đã đặt và giờ thực nhận để không thông báo trong giờ ngủ hay phát bù sau giờ ngủ. Lịch tự khôi phục sau khởi động lại, cập nhật ứng dụng, đổi ngày giờ hoặc múi giờ. Cần cấp quyền thông báo; thiếu quyền nhắc đúng giờ thì Android có thể gửi trễ.

Bấm **Ghi nhận**, chuyển sang tab **Vitamin**, chọn ngày giờ uống rồi lưu. Tab Vitamin chỉ có ngày giờ, không có lượng đã uống hoặc lượng đã bỏ. Nếu ngày được chọn đã có ghi nhận uống Vitamin K2D3, popup hiển thị cảnh báo canh giữa, không kèm ngày giờ và vô hiệu hóa nút Lưu để chặn ghi trùng. Trong **Báo cáo**, ô **Vitamin** thay cho Số cữ hiển thị **Có / Không** theo ngày đang xem, kể cả ngày chưa có cữ sữa. Không hiển thị khung Vitamin K2D3 riêng. Để xóa ghi nhận vitamin, bấm **Xóa**, chọn dòng vitamin (có thể chọn cùng các cữ sữa), rồi **Xác nhận**. **Bỏ qua** không thay đổi dữ liệu. Mỗi ngày có một trạng thái riêng; ngày mới chưa có ghi nhận hiển thị **Không**. Ghi nhận vitamin không thay đổi lịch nhắc hay thống kê sữa.

Trạng thái và thao tác bỏ đánh dấu được đồng bộ qua Drive; khi hai máy sửa cùng ngày, thay đổi mới nhất được giữ. Cả hai máy cần cập nhật lên bản hỗ trợ Vitamin K2D3 trước khi đồng bộ dữ liệu này.

Trong Báo cáo, lần uống Vitamin K2D3 hiển thị cùng danh sách thời gian các cữ sữa, theo thứ tự mới nhất trước. Vitamin không cộng vào tổng ml hoặc số cữ. Màn hình xem không có nút bỏ đánh dấu riêng; chế độ Xóa cho phép chọn cả sữa và vitamin. Báo cáo không hiển thị dòng trung bình mỗi cữ hoặc tiêu đề Các cữ sữa.

## Google Drive cho hai người

Ứng dụng dùng Google Identity Services AuthorizationClient; không nhúng mật khẩu, client secret hay refresh token. Cần cấu hình Google Cloud trước khi đăng nhập hoạt động:

1. Tạo Google Cloud project, bật **Google Drive API**.
2. Cấu hình OAuth consent, thêm tài khoản của bố và mẹ làm test users khi còn ở chế độ Testing.
3. Tạo OAuth client loại **Android**, package `triplesix.babyfeed`, SHA-1 của chứng chỉ ký bản APK (`gradle :app:signingReport`). Với bản release cần đăng ký SHA-1 release tương ứng.
4. Scope `https://www.googleapis.com/auth/drive` được dùng để truy cập thư mục dùng chung bằng ID giữa hai tài khoản. Đây là scope restricted; phát hành rộng rãi có thể cần Google verification. App chỉ liệt kê và ghi trong thư mục người dùng chọn. Không dùng appDataFolder vì không chia sẻ được giữa hai tài khoản. Một phiên bản phát hành rộng rãi nên dùng Google Picker cùng scope drive.file để giảm quyền.
5. Trên máy bố: tab **Thiết Lập**, bấm **Liên kết** Google Drive. Mở thư mục `BabyFeed` trong Drive, chia sẻ với email mẹ với quyền **Chỉnh sửa**.
6. Trên máy mẹ: tab **Thiết Lập**, bấm **Liên kết** Google Drive bằng tài khoản mẹ. App tìm thư mục BabyFeed đã được chia sẻ.

Định dạng sync v1 lưu một bộ file theo năm cho mỗi thiết bị (`babyfeed-v1-<device>-<year>-<part>.json`). Chỉ tách phần mới khi đạt 512 bản ghi hoặc 256 KiB UTF-8, tùy giới hạn nào đến trước. Không tạo file cho từng thao tác hoặc từng tháng trống. Mỗi máy chỉ ghi bộ file của mình để tránh ghi đè giữa hai máy. Nhật ký được rút gọn: giữ kết quả mới nhất của từng cữ, trạng thái lịch hiện hành và các dấu xóa; bỏ thao tác trung gian đã lỗi thời. Dấu xóa được giữ lâu dài để thiết bị offline không phục hồi cữ đã xóa. Tổng dữ liệu vẫn tăng theo lịch sử cữ sữa được giữ, không có giới hạn dung lượng tổng cố định. Cache nội bộ theo ID thư mục, ID file và Drive version giúp không tải lại file không đổi; cache của phiên bản cũ được dọn sau mỗi lần liệt kê thành công. Chỉ ghi lại các phần có nội dung thay đổi. Khi đổi thư mục, toàn bộ lịch sử còn giữ trên máy được tải sang thư mục mới. Chuyển đổi đọc định dạng cũ, ghi từng phần mới và tải lại xác minh trước khi chuyển file cũ vào thùng rác và rút gọn SQLite; lỗi giữa chừng vẫn giữ nguồn để thử lại. Nếu thiếu quyền dọn, app giữ file cũ và báo chủ sở hữu dọn. Cần cập nhật cả hai máy sang bản hỗ trợ sync v1; phiên bản cũ không đọc được định dạng mới. Nếu hai máy ghi kết quả khác nhau cho cùng cữ khi offline, lịch sử chọn kết quả có thời gian mới nhất (UUID phân xử khi bằng nhau); các bản ghi gốc vẫn được giữ. Hai cữ độc lập do hai máy tự tạo vẫn được giữ riêng.

Lịch hiện hành chọn thao tác mới nhất theo thời gian thiết bị; cần bật ngày giờ tự động trên cả hai máy. Hai thiết bị đều có thể nhắc. Đồng bộ định kỳ bằng WorkManager mỗi 15 phút khi có mạng, và được yêu cầu ngay sau thay đổi; không phải đồng bộ tức thời. Android có thể trì hoãn khi tiết kiệm pin. Thông báo đã phát trên máy kia có thể còn trong khay cho đến khi được mở. Khi cần phối hợp ngay, dùng **Đồng bộ ngay** trước khi ghi nhận trên máy thứ hai.

Khi ngắt liên kết, app ngừng dùng thư mục trên máy này nhưng giữ lịch sử. Nếu chuyển thư mục, lịch sử hiện có cũng sẽ được tải lên thư mục mới. Có thể thu hồi quyền của app tại trang quyền tài khoản Google. Dữ liệu trên Drive là JSON, bảo vệ bằng quyền chia sẻ của Drive; không phải mã hóa đầu cuối. Không chia sẻ thư mục công khai.

## Build và kiểm thử

Code Java dùng thụt lề 4 dấu cách theo Google Java Format (AOSP). Chạy `./scripts/format-java.ps1` để định dạng, hoặc `./scripts/format-java.ps1 -Check` để kiểm tra. Script tải formatter tương thích JDK 17 và xác minh SHA-256; `.editorconfig` quy định UTF-8, LF và thụt lề cho các file nguồn.

Các test hồi quy bao phủ phiên Drive bị ngắt, từ chối dữ liệu tải về vượt giới hạn/sai cấu trúc trước khi nhập, cập nhật lịch nhắc trước bước upload có thể lỗi, giữ lịch vitamin đang chờ, và không phát bù nhắc sữa thuộc giờ ngủ. Test trên thiết bị kiểm tra thêm giao dịch SQLite từ nhiều `Store`, chặn popup cũ ghi đè trạng thái mới và giữ trạng thái khi nhập dữ liệu lỗi. Có thể build với `-PisolatedTests` để dùng application ID `triplesix.babyfeed.reviewtests`, tách riêng khỏi ứng dụng gia đình.

Yêu cầu JDK 17, Android SDK platform 35 / build-tools 35.0.0, Gradle 8.11.1. Mở thư mục bằng Android Studio hoặc dùng Gradle:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK tại `app/build/outputs/apk/debug/Baby Feed.apk`. `local.properties` có thể chỉ định `sdk.dir` tới SDK trên máy. Không commit khóa ký.

Test tự động kiểm tra tính lượng uống, giới hạn lượng còn lại, và tính thời điểm cữ tiếp theo. Cần kiểm tra trên điện thoại thực: thông báo khi khóa màn hình, khởi động lại, từ chối/cấp lại quyền, hai tài khoản Drive, offline rồi online, cập nhật đồng thời. Không bật nhắc đúng giờ thì app dùng nhắc gần đúng và hiển thị cảnh báo. Force-stop app bằng Settings có thể chặn nhắc và tác vụ nền cho đến khi mở lại.

Tài liệu: [Android alarms](https://developer.android.com/develop/background-work/services/alarms), [Google authorization](https://developer.android.com/identity/authorization), [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth), [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
