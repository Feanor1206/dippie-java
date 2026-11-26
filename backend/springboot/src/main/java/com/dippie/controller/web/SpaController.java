package com.dippie.controller.web;

import com.dippie.service.web.SpaIndex;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

// Chuyển tiếp các đường dẫn giao diện SPA về file index.html để React Router tự xử lý.
@Controller
public class SpaController {

    private final SpaIndex spaIndex;

    public SpaController(SpaIndex spaIndex) {
        this.spaIndex = spaIndex;
    }

    @RequestMapping(
            path = {"/", "/search", "/profile", "/account", "/admin/import", "/admin/users", "/admin/stats", "/admin/audit",
                    "/movies/{id}", "/series", "/series/{id}", "/lich-su", "/admin/reports", "/dieu-khoan",
                    "/quyen-rieng-tu", "/dat-lai-mat-khau", "/quen-mat-khau"},
            method = {RequestMethod.GET, RequestMethod.HEAD}
    )
    public ResponseEntity<Resource> index() {
        return spaIndex.index();
    }
}
