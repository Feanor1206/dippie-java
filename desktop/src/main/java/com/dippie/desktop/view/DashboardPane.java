package com.dippie.desktop.view;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.export.CsvStrategy;
import com.dippie.desktop.model.DashboardSnapshot;
import com.dippie.desktop.model.MostWatched;
import com.dippie.desktop.model.TopRated;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

// tab Tổng quan (kế thừa DataPane), gom 5 thẻ số liệu, 2 bảng xếp hạng, biểu đồ cột và tự làm mới mỗi 30 giây bằng Timeline
public final class DashboardPane extends DataPane {
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private final AdminController controller;
    private final ObservableList<TopRated> topRows = FXCollections.observableArrayList();
    private final ObservableList<MostWatched> watchedRows = FXCollections.observableArrayList();
    private final Label seriesCountLabel = metricValueLabel();
    private final Label episodeCountLabel = metricValueLabel();
    private final Label userCountLabel = metricValueLabel();
    private final Label ratingCountLabel = metricValueLabel();
    private final Label progressCountLabel = metricValueLabel();
    private final NumberAxis countAxis = new NumberAxis();
    private final XYChart.Series<String, Number> distribution = new XYChart.Series<>();
    private final Timeline refreshTimer;

    public DashboardPane(UiTasks tasks, AdminController controller) {
        super(tasks);
        this.controller = controller;

        // 5 thẻ số liệu tổng ở hàng trên cùng
        var totals = new HBox(14,
                metricCard("Bộ phim", "trong danh mục", seriesCountLabel, "metric-accent-sage"),
                metricCard("Tập phim", "tổng số tập", episodeCountLabel, "metric-accent-leaf"),
                metricCard("Người dùng", "tài khoản đã đăng ký", userCountLabel, "metric-accent-mint"),
                metricCard("Đánh giá", "lượt chấm điểm", ratingCountLabel, "metric-accent-honey"),
                metricCard("Tiến độ xem", "lượt lưu tiến độ", progressCountLabel, "metric-accent-teal"));

        // bảng top 10 tập điểm cao nhất, tắt sort cột để giữ nguyên thứ tự xếp hạng từ DB
        var top = table(topRows, "Chưa có tập nào được chấm điểm",
                "Khi có người chấm điểm tập phim, bảng xếp hạng sẽ hiện ở đây.");
        top.getColumns().add(column("Tập phim", 190, TopRated::title));
        top.getColumns().add(column("Bộ phim", 145, TopRated::seriesTitle));
        var averageColumn = numericColumn("Điểm TB", 86,
                (TopRated row) -> Math.round(row.average() * 100) / 100.0);
        averageColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double averageScore, boolean empty) {
                super.updateItem(averageScore, empty);
                if (empty || averageScore == null) {
                    setText(null);
                    return;
                }
                setText(String.format(VIETNAMESE, "%.2f", averageScore));
            }
        });
        top.getColumns().add(averageColumn);
        top.getColumns().add(numericColumn("Lượt", 68, TopRated::count));
        top.getColumns().forEach(column -> column.setSortable(false));

        // bảng mấy bộ phim nhiều người xem nhất
        var watched = table(watchedRows, "Chưa có ai xem phim",
                "Chỉ tính những tài khoản đã bắt đầu xem ít nhất một tập.");
        watched.getColumns().add(column("Bộ phim", 220, MostWatched::title));
        watched.getColumns().add(numericColumn("Số người xem", 115, MostWatched::viewers));
        watched.getColumns().forEach(column -> column.setSortable(false));
        watched.setPrefHeight(165);

        // chia 2 cột bằng SplitPane, trái là top 10 tập còn phải là biểu đồ điểm và phim xem nhiều
        var leftCard = sectionCard("10 tập điểm cao nhất",
                "Xếp theo điểm trung bình và số lượt chấm", top);
        VBox.setVgrow(top, Priority.ALWAYS);

        var chart = ratingChart();
        var chartCard = sectionCard("Phân bố điểm",
                "Số lượt chấm theo từng mức từ 1 đến 10", chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        VBox.setVgrow(chartCard, Priority.ALWAYS);

        var watchedCard = sectionCard("Bộ phim có nhiều người xem nhất",
                "Số tài khoản đã mở xem từng bộ phim", watched);
        VBox.setVgrow(watched, Priority.ALWAYS);

        var rightColumn = new VBox(14, chartCard, watchedCard);
        var rankings = new SplitPane(leftCard, rightColumn);
        rankings.setDividerPositions(0.54);
        VBox.setVgrow(rankings, Priority.ALWAYS);

        var body = new VBox(16, totals, rankings);
        setContent(body);

        // thanh công cụ gồm dòng báo tự làm mới, nút Làm mới và nút Xuất CSV
        var strategy = new CsvStrategy<TopRated>(List.of("Tập phim", "Bộ phim", "Điểm TB", "Lượt"),
                row -> List.of(row.title(), row.seriesTitle(), Math.round(row.average() * 100) / 100.0, row.count()));
        Label autoNote = new Label("Tự cập nhật mỗi 30 giây");
        autoNote.getStyleClass().add("muted-text");
        Button export = exportButton(top, strategy, "tap-danh-gia-cao.csv");
        export.getStyleClass().remove("secondary-button");
        export.getStyleClass().add("primary-button");
        HBox actions = new HBox(10, refreshButton(), export);
        actions.setAlignment(Pos.CENTER_RIGHT);
        setTop(splitToolbar(autoNote, actions));

        // cứ 30 giây tự tải lại 1 lần, luồng nền đang bận việc khác thì nhường lượt cho khỏi xếp hàng
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(30), event -> {
            if (!tasks.busyProperty().get()) {
                load(false);
            }
        }));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    @Override
    public void refresh() {
        load(true);
    }

    // chỉ bật lớp phủ đang tải khi bấm Làm mới hoặc lúc mới vào, còn lượt tự làm mới thì chạy êm
    private void load(boolean showIndicator) {
        if (showIndicator) {
            setLoading(true);
        }
        controller.dashboard(this::display, () -> setLoading(false));
    }

    public void stop() {
        refreshTimer.stop();
    }

    // cập nhật 5 thẻ tổng, 2 bảng xếp hạng và vẽ lại biểu đồ cột sau mỗi lần đọc
    private void display(DashboardSnapshot snapshot) {
        var counts = snapshot.counts();
        var numbers = NumberFormat.getIntegerInstance(VIETNAMESE);
        seriesCountLabel.setText(numbers.format(counts.series()));
        episodeCountLabel.setText(numbers.format(counts.episodes()));
        userCountLabel.setText(numbers.format(counts.users()));
        ratingCountLabel.setText(numbers.format(counts.ratings()));
        progressCountLabel.setText(numbers.format(counts.progress()));

        topRows.setAll(snapshot.topRated());
        watchedRows.setAll(snapshot.mostWatched());

        // cột cao nhất từ 8 trở xuống thì khóa bước nhảy trục tung là 1 kẻo JavaFX hiện số lẻ kiểu 0.5 hay 1.5
        long peak = snapshot.ratingCounts().values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (peak <= 8) {
            countAxis.setAutoRanging(false);
            countAxis.setLowerBound(0);
            countAxis.setUpperBound(Math.max(4, peak + 1));
            countAxis.setTickUnit(1);
        } else {
            countAxis.setAutoRanging(true);
        }

        distribution.getData().clear();
        for (int score = 1; score <= 10; score++) {
            long total = snapshot.ratingCounts().getOrDefault(score, 0L);
            var bar = new XYChart.Data<String, Number>(Integer.toString(score), total);
            int currentScore = score;
            bar.nodeProperty().addListener((property, oldNode, node) -> {
                if (node != null) {
                    Tooltip.install(node, new Tooltip("Điểm " + currentScore + ": " + numbers.format(total) + " lượt"));
                }
            });
            distribution.getData().add(bar);
        }
    }

    // dựng biểu đồ cột phân bố điểm từ 1 đến 10, tắt animation cho mỗi lần cập nhật đỡ giật
    private BarChart<String, Number> ratingChart() {
        var scores = new CategoryAxis();
        scores.setLabel("Điểm");
        scores.setAnimated(false);
        scores.setCategories(FXCollections.observableArrayList(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        countAxis.setLabel("Số lượt");
        countAxis.setAnimated(false);
        countAxis.setMinorTickVisible(false);
        countAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number number) {
                double numeric = number.doubleValue();
                return Math.abs(numeric - Math.rint(numeric)) < 1e-6 ? Long.toString(Math.round(numeric)) : "";
            }

            @Override
            public Number fromString(String text) {
                return Long.parseLong(text);
            }
        });

        var chart = new BarChart<>(scores, countAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(12);
        chart.setBarGap(2);
        chart.setPrefHeight(220);
        chart.setMinHeight(165);
        chart.getData().add(distribution);
        return chart;
    }

    private static Label metricValueLabel() {
        var label = new Label("-");
        label.getStyleClass().add("metric-value");
        return label;
    }

    // tạo thẻ chỉ số kèm vạch màu phía trên và dòng chú thích nhỏ
    private static VBox metricCard(String title, String hint, Label value, String accentClass) {
        Region stripe = new Region();
        stripe.getStyleClass().addAll("metric-accent", accentClass);
        Label heading = new Label(title);
        heading.getStyleClass().add("metric-label");
        Label caption = new Label(hint);
        caption.getStyleClass().add("metric-hint");

        var card = new VBox(5, stripe, heading, value, caption);
        card.getStyleClass().add("metric");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // bọc bảng hoặc biểu đồ vào khung sáng có tiêu đề và mô tả phụ
    private static VBox sectionCard(String title, String subtitle, javafx.scene.Node content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label description = new Label(subtitle);
        description.getStyleClass().add("section-subtitle");
        VBox header = new VBox(2, heading, description);
        VBox card = new VBox(12, header, content);
        card.getStyleClass().add("surface-card");
        return card;
    }
}
