import { Outlet } from 'react-router-dom';
import Navbar from './Navbar'; // Tùy đường dẫn của ông anh

// [BỔ SUNG] Nhận Props từ App.jsx
export default function MainLayout({ isLoggedIn, onLogout }) {
  return (
    <div className="min-h-screen flex flex-col bg-brand-surface">
      {/* [BỔ SUNG] Truyền Props tiếp cho Navbar */}
      <Navbar isLoggedIn={isLoggedIn} onLogout={onLogout} />
      
      <main className="flex-grow w-full">
        {/* Outlet là nơi các page con sẽ được render vào */}
        <Outlet />
      </main>
      
      <footer className="bg-brand-dark text-gray-400 py-6 text-center text-sm mt-auto">
        <p>© 2026 Transparent Charity Ledger. Secured by PPoS Consensus.</p>
      </footer>
    </div>
  );
}