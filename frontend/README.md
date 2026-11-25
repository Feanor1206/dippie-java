# Giao diện Dippie

React và TypeScript được Vite build thành tệp tĩnh trong `dist/`. Backend Spring Boot phục vụ các tệp này cùng API tại cổng `3000`.

## Làm việc trên giao diện

Khởi động backend Java ở cổng `3000` (ví dụ `.\start-java.ps1` ở thư mục gốc, xem thêm [tài liệu backend](../backend/springboot/README.md)). Từ thư mục `frontend`, chạy các lệnh sau và mở [Dippie](http://localhost:5173/):

```powershell
npm ci
npm run dev
```

Máy chủ phát triển Vite chạy ở `http://127.0.0.1:5173` và chuyển tiếp `/api` tới `http://127.0.0.1:3000`. Có thể đặt biến môi trường `BACKEND_INTERNAL_URL` để trỏ tới địa chỉ backend khác.

## Kiểm thử và build

```powershell
npm run typecheck
npm test
npm run build
```
