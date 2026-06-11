// Default to same-origin: in Docker, nginx proxies /api/ to the backend
// container, so the site works from any device that can reach the host
// (other laptops/phones on the same network), not just localhost.
// `npm run dev` outside Docker still targets localhost:8080 via .env.development.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(/\/$/, "");

export async function apiRequest(path, options = {}) {
  // [BỔ SUNG] Bọc toàn bộ logic vào khối try để bắt mọi loại lỗi mạng
  try {
    // Never attach a stored token to login/register: a stale token from a
    // previous session (or another deployment) must not be able to break
    // creating a brand-new session.
    const isAuthEndpoint = path.startsWith("/api/v1/auth/");
    const token = isAuthEndpoint ? null : localStorage.getItem("accessToken");
    const hasBody = options.body !== undefined && options.body !== null;

    const headers = {
      Accept: "application/json",
      ...(hasBody ? { "Content-Type": "application/json" } : {}),
      ...(options.headers ?? {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    };

    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers
    });

    const text = await response.text();
    let data = null;

    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }

    if (!response.ok) {
      const message = typeof data === "object" && data !== null
        ? data.message ?? "API request failed"
        : response.statusText || "API request failed";
      const error = new Error(message);
      error.status = response.status;
      error.data = data;

      if (response.status === 401) {
        localStorage.removeItem("accessToken");
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      }

      if (response.status === 404) {
        console.warn("404: Resource does not exist");
      }

      throw error;
    }

    return data;

  } catch (systemError) {
    // [BỔ SUNG] Khối catch này sẽ tóm gọn lỗi sập mạng khi chưa bật Backend
    // Nếu là lỗi do chúng ta chủ động throw ở trên (đã có thuộc tính status), cứ thế ném tiếp ra ngoài
    if (systemError.status) {
      throw systemError;
    }

    // Nếu là lỗi hệ thống thô (như sập mạng, sập nguồn server), đóng gói lại chuẩn chỉnh 100%
    const networkError = new Error("Không thể kết nối đến server Backend (8080)!");
    networkError.status = 500;
    networkError.data = null;
    throw networkError;
  }
}