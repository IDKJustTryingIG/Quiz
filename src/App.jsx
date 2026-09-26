import { BrowserRouter, Route, Routes } from "react-router";
import Layout from "@/components/Layout";
import RequireAuth from "@/components/RequireAuth";
import Account from "@/pages/Account";
import Battle from "@/pages/Battle";
import Home from "@/pages/Home";
import Login from "@/pages/Login";
import OAuthFailure from "@/pages/OAuthFailure";
import OAuthSuccess from "@/pages/OAuthSuccess";
import Profile from "@/pages/Profile";
import Quiz from "@/pages/Quiz";
import Signup from "@/pages/Signup";
import Tracks from "@/pages/Tracks";
const App = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="tracks" element={<Tracks />} />
          <Route path="quiz" element={<Quiz />} />
          <Route path="battle" element={<Battle />} />
          <Route element={<RequireAuth />}>
            <Route path="profile" element={<Profile />} />
            <Route path="account" element={<Account />} />
          </Route>
        </Route>
        <Route path="login" element={<Login />} />
        <Route path="signup" element={<Signup />} />
        <Route path="oauth2/success" element={<OAuthSuccess />} />
        <Route path="oauth2/failure" element={<OAuthFailure />} />
      </Routes>
    </BrowserRouter>
  );
};
export default App;
