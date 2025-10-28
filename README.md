# Screen Translator (Accessibility + Overlay)

- Không UI chính. Lấy text qua Accessibility và dịch sang tiếng Việt bằng ML Kit, hiển thị lớp nổi.
- Dự án tối thiểu để build từ GitHub Actions.

## Bật tính năng
1. Cài APK (debug) từ artifact của GitHub Actions.
2. Cài đặt → Trợ năng (Accessibility) → Screen Translator → Bật.

## Build từ GitHub Actions
- Push mã nguồn lên nhánh `main`.
- Workflow `Android CI` tự chạy → tải APK ở phần Artifacts.

## Lưu ý
- Mặc định dịch sang tiếng Việt (đổi trong `TranslatorA11yService.kt`).
- OCR chưa bật.
