import { BrowserRouter, Route, Routes } from "react-router";
import Layout from "@/components/Layout";
import RequireAuth from "@/components/RequireAuth";
import Account from "@/pages/Account";
import AdminQuizJobs from "@/pages/AdminQuizJobs";
import Battle from "@/pages/Battle";
import Contest from "@/pages/Contest";
import Home from "@/pages/Home";
import Login from "@/pages/Login";
import OAuthFailure from "@/pages/OAuthFailure";
import OAuthSuccess from "@/pages/OAuthSuccess";
import Profile from "@/pages/Profile";
import Quiz from "@/pages/Quiz";
import Signup from "@/pages/Signup";
import TopicQuiz from "@/pages/TopicQuiz";
import TrackDetail from "@/pages/TrackDetail";
import Unsubscribe from "@/pages/Unsubscribe";
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
          <Route path="unsubscribe" element={<Unsubscribe />} />
          <Route element={<RequireAuth />}>
            <Route path="profile" element={<Profile />} />
            <Route path="account" element={<Account />} />
            <Route path="admin/quiz-jobs" element={<AdminQuizJobs />} />
            <Route path="contest" element={<Contest />} />
            <Route path="tracks/:slug" element={<TrackDetail />} />
            <Route
              path="tracks/:slug/topics/:topicId/quiz"
              element={<TopicQuiz />}
            />
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
