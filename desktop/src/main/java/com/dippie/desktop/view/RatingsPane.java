package com.dippie.desktop.view;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.db.UtcTime;
import com.dippie.desktop.export.CsvStrategy;
import com.dippie.desktop.model.RatingRow;
import com.dippie.desktop.model.SearchText;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

// tab Đánh giá (kế thừa DataPane), lọc theo từ khóa, bộ phim, mức điểm và xóa đánh giá sau khi xác nhận
public final class RatingsPane extends DataPane {
    private static final SeriesChoice ALL = new SeriesChoice(null, "Tất cả bộ phim");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final AdminController controller;
    private final ObservableList<RatingRow> rows = FXCollections.observableArrayList();
    private final FilteredList<RatingRow> filtered = new FilteredList<>(rows);
    private final TableView<RatingRow> table;
    private final TextField search = new TextField();
    private final ComboBox<SeriesChoice> series = new ComboBox<>();
    private final ComboBox<String> score = new ComboBox<>();
    private final Button clearFilter = new Button("Xóa lọc");
    private final Label resultCount = pill("0 đánh giá", "pill-neutral");

    public RatingsPane(UiTasks tasks, AdminController controller) {
        super(tasks);
        this.controller = controller;
        var sorted = new SortedList<>(filtered);
        table = table(sorted, "Chưa có đánh giá nào",
                "Khi người dùng chấm điểm phim, danh sách sẽ hiện ở đây.");
        sorted.comparatorProperty().bind(table.comparatorProperty());

        // dựng bảng đánh giá kèm nhãn màu cho điểm và cột giờ format dd/MM/yyyy HH:mm
        table.getColumns().add(numericColumn("Mã", 64, RatingRow::id));
        table.getColumns().add(column("Người dùng", 130, RatingRow::username));
        table.getColumns().add(column("Nội dung", 250, RatingRow::title));
        table.getColumns().add(column("Bộ phim", 185, RatingRow::seriesTitle));

        var scoreColumn = column("Điểm", 92, (RatingRow row) -> row.rating().score());
        scoreColumn.getStyleClass().add("center-cell");
        scoreColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer ratingScore, boolean empty) {
                super.updateItem(ratingScore, empty);
                if (empty || ratingScore == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String style = ratingScore >= 8 ? "pill-sage" : ratingScore >= 5 ? "pill-honey" : "pill-danger";
                setText(null);
                setGraphic(pill(ratingScore + " / 10", style));
            }
        });
        table.getColumns().add(scoreColumn);

        var updatedColumn = column("Cập nhật", 160, (RatingRow row) -> row.rating().updatedAt());
        updatedColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime time, boolean empty) {
                super.updateItem(time, empty);
                setText(empty || time == null ? null : TIME_FORMAT.format(UtcTime.toLocal(time)));
            }
        });
        table.getColumns().add(updatedColumn);

        // bộ lọc nhanh theo từ khóa, bộ phim và mức điểm từ 1 đến 10
        search.setPromptText("Tìm người dùng, phim... (Ctrl+F)");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(220);
        search.setTooltip(new Tooltip("Tìm theo tên người dùng, tên tập hoặc bộ phim (Ctrl+F)"));
        search.textProperty().addListener((property, previous, text) -> applyFilter());

        series.getItems().setAll(ALL);
        series.setValue(ALL);
        series.setTooltip(new Tooltip("Lọc theo bộ phim"));
        series.setMaxWidth(210);
        score.getItems().add("Tất cả mức điểm");
        score.getItems().addAll(IntStream.rangeClosed(1, 10).mapToObj(Integer::toString).toList());
        score.getSelectionModel().selectFirst();
        score.setTooltip(new Tooltip("Lọc theo điểm từ 1 đến 10"));

        series.setOnAction(event -> applyFilter());
        score.setOnAction(event -> applyFilter());

        Label seriesLabel = new Label("Bộ phim");
        seriesLabel.getStyleClass().add("field-label");
        seriesLabel.setLabelFor(series);
        Label scoreLabel = new Label("Điểm");
        scoreLabel.getStyleClass().add("field-label");
        scoreLabel.setLabelFor(score);

        clearFilter.getStyleClass().add("ghost-button");
        clearFilter.setTooltip(new Tooltip("Xóa hết các bộ lọc đang chọn"));
        clearFilter.setVisible(false);
        clearFilter.setManaged(false);
        clearFilter.setOnAction(event -> {
            search.clear();
            series.setValue(ALL);
            score.getSelectionModel().selectFirst();
            applyFilter();
        });

        var strategy = new CsvStrategy<RatingRow>(
                List.of("Mã", "Người dùng", "Nội dung", "Bộ phim", "Điểm", "Cập nhật"),
                row -> List.of(row.id(), row.username(), row.title(), row.seriesTitle(), row.rating().score(),
                        row.rating().updatedAt() == null ? "" : TIME_FORMAT.format(UtcTime.toLocal(row.rating().updatedAt()))));
        setTop(toolbar(search, seriesLabel, series, scoreLabel, score, clearFilter, resultCount,
                refreshButton(), exportButton(table, strategy, "danh-gia.csv"), deleteButton()));
        setContent(table);
    }

    @Override
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    // lỡ vừa xóa đánh giá cuối cùng của bộ phim đang lọc thì vẫn giữ bộ đó trong hộp chọn
    // để người dùng thấy bảng trống chứ bộ lọc không tự nhảy sang Tất cả bộ phim
    @Override
    public void refresh() {
        setLoading(true);
        controller.ratings(ratingRows -> {
            var selectedSeries = series.getValue() == null ? ALL : series.getValue();
            var choices = ratingRows.stream().map(row -> new SeriesChoice(row.seriesId(), row.seriesTitle()))
                    .distinct().sorted(Comparator.comparing(SeriesChoice::title)).toList();

            series.getItems().setAll(ALL);
            series.getItems().addAll(choices);
            if (!series.getItems().contains(selectedSeries)) {
                series.getItems().add(selectedSeries);
            }
            series.setValue(selectedSeries);
            rows.setAll(ratingRows);
            applyFilter();
        }, () -> setLoading(false));
    }

    // ghép cả 3 điều kiện lọc rồi cập nhật bộ đếm với câu báo lúc bảng trống
    private void applyFilter() {
        String term = SearchText.normalize(search.getText());
        var selectedSeries = series.getValue();
        int selectedScore = score.getSelectionModel().getSelectedIndex();
        boolean hasSeriesFilter = selectedSeries != null && !selectedSeries.equals(ALL);
        boolean hasFilter = !term.isEmpty() || hasSeriesFilter || selectedScore > 0;

        filtered.setPredicate(row -> (!hasSeriesFilter || Objects.equals(selectedSeries.id(), row.seriesId()))
                && (selectedScore <= 0 || selectedScore == row.rating().score())
                && (term.isEmpty() || SearchText.contains(row.username(), term)
                        || SearchText.contains(row.title(), term)
                        || SearchText.contains(row.seriesTitle(), term)));

        clearFilter.setVisible(hasFilter);
        clearFilter.setManaged(hasFilter);
        int visible = filtered.size();
        int total = rows.size();
        resultCount.setText(hasFilter ? visible + " / " + total + " đánh giá" : total + " đánh giá");
        table.setPlaceholder(hasFilter
                ? emptyPlaceholder("Không có đánh giá nào khớp", "Thử đổi bộ lọc hoặc bấm Xóa lọc để xem lại tất cả.")
                : emptyPlaceholder("Chưa có đánh giá nào", "Khi người dùng chấm điểm phim, danh sách sẽ hiện ở đây."));
    }

    // hỏi xác nhận ghi rõ điểm, người chấm và tên phim trước khi gọi controller xóa
    private Button deleteButton() {
        var button = new Button("Xóa đánh giá");
        button.getStyleClass().add("danger-button");
        button.setTooltip(new Tooltip("Xóa đánh giá đang chọn và lưu vào nhật ký quản trị"));
        var selection = table.getSelectionModel().selectedItemProperty();
        button.disableProperty().bind(tasks.busyProperty().or(selection.isNull()));
        button.setOnAction(event -> {
            var selected = selection.get();
            String content = "Xóa đánh giá %d/10 của %s cho \"%s\"? Thao tác này sẽ được lưu lại trong nhật ký quản trị."
                    .formatted(selected.rating().score(), selected.username(), selected.title());
            if (tasks.confirmDanger("Xóa đánh giá", content, "Xóa đánh giá")) {
                controller.deleteRating(selected);
            }
        });
        return button;
    }

    // gói seriesId với tên hiển thị cho ComboBox chọn bộ phim
    private record SeriesChoice(String id, String title) {
        @Override
        public String toString() {
            return title;
        }
    }
}
