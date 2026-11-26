package com.dippie.desktop.service;

import com.dippie.desktop.dao.AuditDao;
import com.dippie.desktop.dao.EpisodeAssetDao;
import com.dippie.desktop.dao.EpisodeDao;
import com.dippie.desktop.dao.MediaAssetDao;
import com.dippie.desktop.dao.MovieDao;
import com.dippie.desktop.dao.RatingDao;
import com.dippie.desktop.dao.SeasonDao;
import com.dippie.desktop.dao.SeriesDao;
import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.dao.WatchProgressDao;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.DashboardSnapshot;
import com.dippie.desktop.model.RatingRow;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;
import com.dippie.desktop.model.catalog.SeriesNode;

import java.util.List;

// Facade gom toàn bộ chức năng quản trị vào một đầu mối và tự lắp sẵn các DAO, để controller không phải tự khởi tạo từng lớp.
public final class AdminServices {
    private final UserDao userDao;
    private final AuthService authService;
    private final UserService userService;
    private final RatingService ratingService;
    private final CatalogService catalogService;
    private final DashboardService dashboardService;

    // Khởi tạo một lần rồi dùng chung các DAO trên cùng JdbcDatabase cho mọi service.
    public AdminServices(JdbcDatabase database) {
        userDao = new UserDao(database);
        var auditDao = new AuditDao(database);
        var ratingDao = new RatingDao(database);
        var seriesDao = new SeriesDao(database);
        var episodeDao = new EpisodeDao(database);

        authService = new AuthService(userDao);
        userService = new UserService(database, userDao, auditDao);
        ratingService = new RatingService(database, ratingDao, new MovieDao(database), userDao, auditDao);
        catalogService = new CatalogService(database, seriesDao, new SeasonDao(database), episodeDao,
                new MediaAssetDao(database), new EpisodeAssetDao(database), ratingDao);
        dashboardService = new DashboardService(database, seriesDao, episodeDao, userDao, ratingDao, new WatchProgressDao(database));
    }

    public AdminSession login(String identifier, String password) {
        return authService.login(identifier, password);
    }

    // Xác minh phiên rồi trả về thông tin quản trị viên đang đăng nhập.
    public UserAccount currentAdmin(AdminSession session) {
        return AdminQuery.requireCurrent(session, userDao.findById(session.userId()).orElse(null));
    }

    public List<UserAccount> users(AdminSession session) {
        return new AdminQuery<>(userDao, session, userService::list).get();
    }

    public List<RatingRow> ratings(AdminSession session) {
        return new AdminQuery<>(userDao, session, ratingService::list).get();
    }

    public List<SeriesNode> catalog(AdminSession session) {
        return new AdminQuery<>(userDao, session, catalogService::load).get();
    }

    public DashboardSnapshot dashboard(AdminSession session) {
        return new AdminQuery<>(userDao, session, dashboardService::load).get();
    }

    public AccountCommand accountCommand(AdminSession session, UserAccount target, Role role, boolean enabled) {
        return new AccountCommand(userService, session, target, role, enabled);
    }

    public void deleteRating(AdminSession session, RatingRow selected) {
        ratingService.delete(session, selected);
    }
}
