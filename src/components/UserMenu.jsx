import { CircleUser, LogIn, LogOut, User, UserPlus } from "lucide-react";
import { Link, useNavigate } from "react-router";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/lib/auth";
const initials = (name) =>
  name
    .split(" ")
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
const UserMenu = () => {
  const user = useAuth((s) => s.user);
  const logout = useAuth((s) => s.logout);
  const navigate = useNavigate();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={user ? "Account menu" : "Sign in menu"}
        className="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      >
        <Avatar>
          {user?.profilePicture && (
            <AvatarImage src={user.profilePicture} alt="" />
          )}
          <AvatarFallback>
            {user ? initials(user.username) : <User className="size-4" />}
          </AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="center" className="w-auto min-w-44">
        {user ? (
          <>
            <DropdownMenuGroup>
              <DropdownMenuLabel className="flex flex-col gap-0.5 py-1.5">
                <span className="text-sm font-medium text-foreground">
                  {user.username}
                </span>
                <span className="truncate font-normal">{user.email}</span>
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem render={<Link to="/profile" />}>
                <User />
                Profile
              </DropdownMenuItem>
              <DropdownMenuItem render={<Link to="/account" />}>
                <CircleUser />
                My account
              </DropdownMenuItem>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => {
                navigate("/", { replace: true });
                // Deferred a tick so the route change commits first. Ending the
                // session in the same batch lets a guarded page's redirect to
                // /login render alongside this navigation and win.
                setTimeout(() => void logout(), 0);
              }}
            >
              <LogOut />
              Log out
            </DropdownMenuItem>
          </>
        ) : (
          <>
            <DropdownMenuItem render={<Link to="/login" />}>
              <LogIn />
              Log in
            </DropdownMenuItem>
            <DropdownMenuItem render={<Link to="/signup" />}>
              <UserPlus />
              Create account
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
export default UserMenu;
