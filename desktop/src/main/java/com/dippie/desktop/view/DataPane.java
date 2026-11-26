package com.dippie.desktop.view;

import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.export.CsvExporter;
import com.dippie.desktop.export.CsvStrategy;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.util.List;
import java.util.function.Function;

// lớp cha trừu tượng của 4 tab dữ liệu, gom sẵn bảng, lớp phủ đang tải, thanh công cụ với nút xuất CSV
// WorkspacePane gọi chung refresh() và focusSearch() qua DataPane này theo đa hình
public abstract class DataPane extends BorderPane {
    protected final UiTasks tasks;
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    protected DataPane(UiTasks tasks) {
        this.tasks = tasks;
        getStyleClass().add("data-pane");
    }

    public abstract void refresh();

    // tab nào có ô tìm kiếm thì ghi đè hàm này để bấm Ctrl+F nhảy thẳng con trỏ vào đó
    public void focusSearch() {
    }

    protected void setLoading(boolean isLoading) {
        loading.set(isLoading);
    }

    // bọc nội dung chính kèm lớp phủ đang tải kẻo bấm nhầm lúc truy vấn chưa xong
    protected void setContent(Node content) {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(32, 32);
        indicator.setMaxSize(32, 32);
        Label loadingText = new Label("Đang tải...");
        loadingText.getStyleClass().add("muted-text");
        VBox loadingBox = new VBox(10, indicator, loadingText);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.getStyleClass().add("loading-box");
        loadingBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        StackPane overlay = new StackPane(loadingBox);
        overlay.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("loading-overlay");
        overlay.visibleProperty().bind(loading);
        overlay.managedProperty().bind(loading);

        StackPane container = new StackPane(content, overlay);
        setCenter(container);
    }

    // thanh công cụ dùng FlowPane để thu hẹp cửa sổ là tự xuống dòng
    protected static FlowPane toolbar(Node... controls) {
        var toolbar = new FlowPane(10, 8, controls);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar-card");
        BorderPane.setMargin(toolbar, new Insets(0, 0, 14, 0));
        return toolbar;
    }

    // đẩy 2 nhóm nút về 2 đầu trái phải trên cùng 1 hàng
    protected static HBox splitToolbar(Node left, Node right) {
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        var bar = new HBox(12, left, gap, right);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("toolbar-card");
        BorderPane.setMargin(bar, new Insets(0, 0, 14, 0));
        return bar;
    }

    protected static <T> TableView<T> table(ObservableList<T> rows) {
        return table(rows, "Chưa có dữ liệu", "Bấm Làm mới hoặc nhấn F5 để tải lại.");
    }

    // tạo bảng sẵn cột tự co giãn theo độ rộng kèm khung báo khi trống
    protected static <T> TableView<T> table(ObservableList<T> rows, String emptyTitle, String emptyHint) {
        var table = new TableView<>(rows);
        table.setPlaceholder(emptyPlaceholder(emptyTitle, emptyHint));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        return table;
    }

    // khung báo trống dùng chung cho bảng và cây lúc chưa có dữ liệu hay lọc không ra
    protected static VBox emptyPlaceholder(String title, String hint) {
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label detail = new Label(hint);
        detail.getStyleClass().add("empty-hint");
        detail.setWrapText(true);
        detail.setMaxWidth(360);
        VBox box = new VBox(6, heading, detail);
        box.getStyleClass().add("empty-state");
        return box;
    }

    // nhãn bo tròn cho trạng thái, vai trò, điểm số hay bộ đếm
    protected static Label pill(String text, String variantClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("pill", variantClass);
        return label;
    }

    // giữ nguyên kiểu gốc V của ô thay vì đổi sang String để JavaFX sort số đúng thứ tự
    protected static <T, V> TableColumn<T, V> column(String title, double width, Function<T, V> extractor) {
        var column = new TableColumn<T, V>(title);
        column.setPrefWidth(width);
        column.setReorderable(false);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(extractor.apply(cell.getValue())));
        return column;
    }

    // cột số căn phải cho dễ dóng hàng
    protected static <T, V> TableColumn<T, V> numericColumn(String title, double width, Function<T, V> extractor) {
        var column = column(title, width, extractor);
        column.getStyleClass().add("numeric-cell");
        return column;
    }

    // chụp bản sao danh sách đang hiện trên bảng (giữ đúng bộ lọc và thứ tự sort) rồi ghi CSV ở luồng nền
    protected <T> Button exportButton(TableView<T> table, CsvStrategy<T> strategy, String filename) {
        var export = new Button("Xuất CSV");
        export.getStyleClass().add("secondary-button");
        export.setTooltip(new Tooltip("Lưu các dòng đang xem thành tệp CSV (UTF-8)"));
        export.disableProperty().bind(tasks.busyProperty());
        export.setOnAction(event -> {
            var rows = List.copyOf(table.getItems());
            var chooser = new FileChooser();
            chooser.setTitle("Lưu tệp CSV");
            chooser.setInitialFileName(filename);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Tệp CSV", "*.csv"));
            var destination = chooser.showSaveDialog(tasks.owner());
            if (destination != null) {
                tasks.file(() -> {
                    new CsvExporter().write(destination.toPath(), strategy, rows);
                    return rows.size();
                }, count -> tasks.message(Alert.AlertType.INFORMATION, "Đã xuất CSV",
                        "Đã lưu " + count + " dòng vào " + destination.getName() + "."));
            }
        });
        return export;
    }

    protected Button refreshButton() {
        Button refresh = new Button("Làm mới");
        refresh.getStyleClass().add("secondary-button");
        refresh.setTooltip(new Tooltip("Tải lại dữ liệu mới nhất (F5)"));
        refresh.disableProperty().bind(tasks.busyProperty());
        refresh.setOnAction(event -> refresh());
        return refresh;
    }
}
