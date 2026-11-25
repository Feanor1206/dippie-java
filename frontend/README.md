# Giao diện Dippie

Giao diện viết bằng React và TypeScript, dùng Vite để build ra tệp tĩnh trong `dist/`. Backend Spring Boot phục vụ luôn thư mục này cùng toàn bộ API trên cổng `3000`.

## Làm việc trên giao diện

Chạy backend Java ở cổng `3000` trước (ví dụ `.\start-java.ps1` ở thư mục gốc, xem thêm [tài liệu backend](../backend/springboot/README.md)). Từ thư mục `frontend`, chạy các lệnh sau rồi mở [Dippie](http://localhost:5173/):

```powershell
npm ci
npm run dev
```

Máy chủ phát triển Vite chạy ở `http://127.0.0.1:5173` và tự chuyển tiếp `/api` sang `http://127.0.0.1:3000`. Nếu backend chạy ở địa chỉ khác, bạn đặt biến môi trường `BACKEND_INTERNAL_URL`.

## Kiểm thử và build

```powershell
npm run typecheck
npm test
npm run build
```
