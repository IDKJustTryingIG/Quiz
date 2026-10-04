import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useTheme } from "@/lib/theme";
const ThemeToggle = () => {
  const theme = useTheme((s) => s.theme);
  const toggleTheme = useTheme((s) => s.toggleTheme);
  const nextTheme = theme === "dark" ? "light" : "dark";
  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggleTheme}
      aria-label={`Switch to ${nextTheme} mode`}
      title={`Switch to ${nextTheme} mode`}
    >
      <Sun className="hidden dark:block" />
      <Moon className="block dark:hidden" />
    </Button>
  );
};
export default ThemeToggle;
