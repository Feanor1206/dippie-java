package com.dippie.desktop;

import com.dippie.desktop.dao.AuditDao;
import com.dippie.desktop.dao.MovieDao;
import com.dippie.desktop.dao.RatingDao;
import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

// test đăng nhập, đổi quyền, ghi audit log và hoàn tác trên DB H2 tạo từ schema thật
class AdminServiceTest extends TestDatabase {
    private final AdminSession admin = new AdminSession(1, 0);

    // dùng BCrypt cost 4 để test chạy cho nhanh mà vẫn đi qua luồng băm thật
    private AuthService auth() throws Exception {
        execute("UPDATE users SET password = ?", new BCryptPasswordEncoder(4).encode("mật khẩu"));
        return new AuthService(new UserDao(db));
    }

    @Test
    void bcryptAllowsActiveAdminByUsernameAndEmail() throws Exception {
        var service = auth();
        assertEquals(admin, service.login(" admin ", "mật khẩu"));
        assertEquals(admin, service.login("admin@dippie.test", "mật khẩu"));
    }

    @Test
    void bcryptRejectsWrongPasswordAndUnknownUser() throws Exception {
        var service = auth();
        assertEquals(AuthException.Reason.INVALID_CREDENTIALS,
                assertThrows(AuthException.class, () -> service.login("admin", "sai")).reason());
        assertEquals(AuthException.Reason.INVALID_CREDENTIALS,
                assertThrows(AuthException.class, () -> service.login("missing", "mật khẩu")).reason());
    }

    @Test
    void bcryptRejectsNonAdmin() throws Exception {
        var service = auth();
        assertEquals(AuthException.Reason.NOT_ADMIN,
                assertThrows(AuthException.class, () -> service.login("viewer", "mật khẩu")).reason());
    }

    @Test
    void bcryptRejectsLockedAdmin() throws Exception {
        var service = auth();
        assertEquals(AuthException.Reason.LOCKED,
                assertThrows(AuthException.class, () -> service.login("locked", "mật khẩu")).reason());
    }

    @Test
    void bcryptUsesTheWebsiteLimitOf72Utf8Bytes() throws Exception {
        String boundary = "á".repeat(36);
        execute("UPDATE users SET password = ? WHERE id = 1", new BCryptPasswordEncoder(4).encode(boundary));
        var service = new AuthService(new UserDao(db));
        assertEquals(admin, service.login("admin", boundary));
        assertEquals(AuthException.Reason.INVALID_CREDENTIALS,
                assertThrows(AuthException.class, () -> service.login("admin", boundary + "a")).reason());
    }

    @Test
    void facadeReadsCurrentDataAndDeniesEveryReadAfterRevocation() throws Exception {
        var facade = new AdminServices(db);
        assertEquals(3, facade.users(admin).size());
        assertEquals(2, facade.catalog(admin).size());
        assertEquals(5, facade.dashboard(admin).counts().ratings());
        assertEquals("Phim cũ", facade.ratings(admin).stream().filter(row -> row.id() == 5)
                .findFirst().orElseThrow().title());

        execute("UPDATE users SET role = 'ROLE_USER', token_version = 1 WHERE id = 1");
        assertThrows(DataAccessException.Conflict.class, () -> facade.users(admin));
        assertThrows(DataAccessException.Conflict.class, () -> facade.ratings(admin));
        assertThrows(DataAccessException.Conflict.class, () -> facade.catalog(admin));
        assertThrows(DataAccessException.Conflict.class, () -> facade.dashboard(admin));
    }

    @Test
    void lockUnlockAndRoleChangeInvalidateWebsiteTokens() {
        var users = new UserDao(db);
        var service = new UserService(db, users, new AuditDao(db));
        var selected = users.findById(2L).orElseThrow();

        var locked = service.change(admin, selected, selected.role(), false);
        assertFalse(locked.enabled());
        var unlocked = service.change(admin, locked, locked.role(), true);
        assertTrue(unlocked.enabled());
        var promoted = service.change(admin, unlocked, Role.ROLE_ADMIN, true);
        assertEquals(Role.ROLE_ADMIN, promoted.role());
        assertEquals(3, users.findById(2L).orElseThrow().tokenVersion());
        assertEquals(3, new AuditDao(db).count());
    }

    @Test
    void accountChangeRollsBackIfAuditInsertFails() throws Exception {
        var users = new UserDao(db);
        var service = new UserService(db, users, new AuditDao(db));
        var selected = users.findById(2L).orElseThrow();
        execute("ALTER TABLE admin_audit_events ADD CONSTRAINT audit_failure CHECK (action = 'DENIED')");

        assertThrows(DataAccessException.Transaction.class,
                () -> service.change(admin, selected, selected.role(), false));
        assertEquals(selected, users.findById(2L).orElseThrow());
        assertEquals(0, new AuditDao(db).count());
    }

    @Test
    void undoRestoresPreviousRoleAndWritesAnotherAudit() {
        var users = new UserDao(db);
        var audit = new AuditDao(db);
        var service = new UserService(db, users, audit);
        var initial = users.findById(2L).orElseThrow();
        var history = new CommandHistory();
        history.execute(new AccountCommand(service, admin, initial, Role.ROLE_ADMIN, true));

        assertTrue(history.canUndo());
        history.undo();
        var restored = users.findById(2L).orElseThrow();
        assertEquals(initial.role(), restored.role());
        assertEquals(2, restored.tokenVersion());
        assertEquals(2, audit.count());
        assertFalse(history.canUndo());
    }

    @Test
    void undoRefusesToOverwriteNewerAccountChanges() throws Exception {
        var users = new UserDao(db);
        var audit = new AuditDao(db);
        var service = new UserService(db, users, audit);
        var initial = users.findById(2L).orElseThrow();
        var history = new CommandHistory();
        history.execute(new AccountCommand(service, admin, initial, initial.role(), false));
        execute("UPDATE users SET token_version = token_version + 1, role = 'ROLE_ADMIN' WHERE id = 2");

        assertThrows(DataAccessException.Conflict.class, history::undo);
        assertFalse(users.findById(2L).orElseThrow().enabled());
        assertEquals(Role.ROLE_ADMIN, users.findById(2L).orElseThrow().role());
        assertEquals(1, audit.count());
    }

    @Test
    void undoAppliesOnlyToTheLastSuccessfulAccountCommand() {
        var users = new UserDao(db);
        var audit = new AuditDao(db);
        var service = new UserService(db, users, audit);
        var history = new CommandHistory();
        var before = users.findById(2L).orElseThrow();
        history.execute(new AccountCommand(service, admin, before, Role.ROLE_ADMIN, true));
        var promoted = users.findById(2L).orElseThrow();
        history.execute(new AccountCommand(service, admin, promoted, promoted.role(), false));

        history.undo();
        var restored = users.findById(2L).orElseThrow();
        assertTrue(restored.enabled());
        assertEquals(Role.ROLE_ADMIN, restored.role());
        assertEquals(3, audit.count());
        assertFalse(history.canUndo());
    }

    @Test
    void rejectsSelfChangeAndRevokedAdminSession() throws Exception {
        var users = new UserDao(db);
        var audit = new AuditDao(db);
        var service = new UserService(db, users, audit);

        assertThrows(DataAccessException.Conflict.class,
                () -> service.change(admin, users.findById(1L).orElseThrow(), Role.ROLE_USER, true));
        execute("UPDATE users SET token_version = 1 WHERE id = 1");
        assertThrows(DataAccessException.Conflict.class,
                () -> service.change(admin, users.findById(2L).orElseThrow(), Role.ROLE_ADMIN, true));
        assertEquals(0, audit.count());
    }

    @Test
    void ratingDeletionAndAuditCommitTogether() {
        var ratings = new RatingDao(db);
        var audit = new AuditDao(db);
        var service = new RatingService(db, ratings, new MovieDao(db), new UserDao(db), audit);
        service.delete(admin, service.list().stream().filter(row -> row.id() == 1).findFirst().orElseThrow());

        assertTrue(ratings.findById(1L).isEmpty());
        var event = audit.findAll().getFirst();
        assertEquals("RATING_DELETED", event.action());
        assertEquals("1", event.targetId());
    }

    @Test
    void ratingDeletionRollsBackIfAuditInsertFails() throws Exception {
        var ratings = new RatingDao(db);
        var audit = new AuditDao(db);
        var service = new RatingService(db, ratings, new MovieDao(db), new UserDao(db), audit);
        var targetRow = service.list().stream().filter(item -> item.id() == 1).findFirst().orElseThrow();
        execute("ALTER TABLE admin_audit_events ADD CONSTRAINT audit_failure CHECK (action = 'DENIED')");

        assertThrows(DataAccessException.Transaction.class, () -> service.delete(admin, targetRow));
        assertEquals(10, ratings.findById(1L).orElseThrow().score());
        assertEquals(0, audit.count());
    }

    @Test
    void ratingDeletionRequiresTheConfirmedVersionAndAnActiveAdmin() throws Exception {
        var ratings = new RatingDao(db);
        var audit = new AuditDao(db);
        var service = new RatingService(db, ratings, new MovieDao(db), new UserDao(db), audit);
        var targetRow = service.list().stream().filter(item -> item.id() == 1).findFirst().orElseThrow();
        execute("UPDATE movie_ratings SET rating = 9 WHERE id = 1");
        assertThrows(DataAccessException.Conflict.class, () -> service.delete(admin, targetRow));
        var updatedRow = service.list().stream().filter(item -> item.id() == 1).findFirst().orElseThrow();
        assertThrows(DataAccessException.Conflict.class, () -> service.delete(new AdminSession(2, 0), updatedRow));
        assertEquals(9, ratings.findById(1L).orElseThrow().score());
        assertEquals(0, audit.count());
    }
}
