import { Outlet } from "react-router";
import Navbar from "./Navbar";
const Layout = () => {
  return (
    <div className="min-h-svh">
      <Navbar />
      <main>
        <Outlet />
      </main>
    </div>
  );
};
export default Layout;
