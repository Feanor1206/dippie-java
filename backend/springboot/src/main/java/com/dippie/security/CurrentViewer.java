package com.dippie.security;

import java.util.Optional;

public interface CurrentViewer {

    Optional<Viewer> find();

    Viewer require();

    Viewer requireAdmin();
}
