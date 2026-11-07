import { NavLink } from "react-router-dom";
export function MovieAdminNav() {
  return (
    <nav className="tabs movie-admin-nav" aria-label="Danh mục điện ảnh">
      <NavLink className="button" to="/admin/movies" end>
        Phim
      </NavLink>
      <NavLink className="button" to="/admin/genres">
        Thể loại
      </NavLink>
      <NavLink className="button" to="/admin/persons">
        Diễn viên & đạo diễn
      </NavLink>
    </nav>
  );
}
