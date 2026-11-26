package com.dippie.desktop.view;

import com.dippie.desktop.controller.AdminController;
import com.dippie.desktop.controller.UiTasks;
import com.dippie.desktop.model.SearchText;
import com.dippie.desktop.model.catalog.CatalogNode;
import com.dippie.desktop.model.catalog.EpisodeNode;
import com.dippie.desktop.model.catalog.SeasonNode;
import com.dippie.desktop.model.catalog.SeriesNode;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Tab Danh mục (kế thừa DataPane). Lớp này làm Adapter chuyển cây Composite chỉ đọc thành TreeItem của JavaFX rồi hiện chi tiết ở khung bên phải.
public final class CatalogPane extends DataPane {
    private final AdminController controller;
    private final TreeView<CatalogNode> tree = new TreeView<>();
    private final TextField search = new TextField();
    private final Label resultCount = pill("0 bộ phim", "pill-neutral");
    private final VBox emptyTreeState = emptyPlaceholder("Không thấy phim nào khớp",
            "Thử từ khóa khác hoặc bấm Xóa lọc để xem lại cả danh mục.");
    private final Label typeBadge = pill("", "pill-sage");
    private final Label title = new Label();
    private final Label originalTitle = new Label();
    private final FlowPane metadataPills = new FlowPane(8, 6);
    private final TextArea synopsis = readOnlyArea();
    private final TextArea sources = readOnlyArea();
    private final VBox emptyDetail;
    private final VBox detailBody;
    private List<SeriesNode> catalog = List.of();

    public CatalogPane(UiTasks tasks, AdminController controller) {
        super(tasks);
        this.controller = controller;

        // Thanh tìm kiếm bên trên kèm nút xóa lọc và bộ đếm số bộ phim đang khớp.
        search.setPromptText("Tìm tên hoặc mã bộ, mùa, tập... (Ctrl+F)");
        search.getStyleClass().add("search-field");
        search.setTooltip(new Tooltip("Tìm theo tên phim, tên gốc hoặc mã (Ctrl+F)"));
        Label searchLabel = new Label("Tìm phim");
        searchLabel.getStyleClass().add("field-label");
        searchLabel.setLabelFor(search);

        Button clearFilter = new Button("Xóa lọc");
        clearFilter.getStyleClass().add("ghost-button");
        clearFilter.setTooltip(new Tooltip("Xóa từ khóa đang tìm"));
        clearFilter.visibleProperty().bind(search.textProperty().isNotEmpty());
        clearFilter.managedProperty().bind(clearFilter.visibleProperty());
        clearFilter.setOnAction(event -> {
            search.clear();
            search.requestFocus();
        });

        HBox searchGroup = new HBox(10, searchLabel, search, clearFilter, resultCount);
        searchGroup.setAlignment(Pos.CENTER_LEFT);
        setTop(splitToolbar(searchGroup, refreshButton()));

        // Tùy biến cell để gắn nhãn Bộ, Mùa, Tập ngay trên cây và cập nhật khung chi tiết khi đổi mục chọn.
        tree.setShowRoot(false);
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(CatalogNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label badge = switch (item) {
                    case SeriesNode series -> pill("Bộ", "pill-sage");
                    case SeasonNode season -> pill("Mùa", "pill-honey");
                    case EpisodeNode episode -> pill("Tập", "pill-neutral");
                };
                Label name = new Label(item.title());
                name.getStyleClass().add("tree-node-title");
                HBox row = new HBox(8, badge, name);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((property, previous, selected) ->
                showDetails(selected == null ? null : selected.getValue()));
        search.textProperty().addListener((property, previous, term) -> showTree());

        emptyTreeState.setVisible(false);
        emptyTreeState.setManaged(false);
        StackPane treeContainer = new StackPane(tree, emptyTreeState);

        // Khung chi tiết bên phải dùng TextArea chỉ đọc để vẫn bôi đen sao chép tóm tắt hay đường dẫn phát được.
        title.getStyleClass().add("section-title");
        title.setWrapText(true);
        originalTitle.getStyleClass().add("muted-text");
        originalTitle.setWrapText(true);
        originalTitle.managedProperty().bind(originalTitle.visibleProperty());
        synopsis.setPrefRowCount(5);
        sources.setPrefRowCount(4);

        Label synopsisLabel = new Label("Tóm tắt");
        synopsisLabel.getStyleClass().add("field-label");
        synopsisLabel.setLabelFor(synopsis);
        Label sourceLabel = new Label("Nguồn YouTube và Drive");
        sourceLabel.getStyleClass().add("field-label");
        sourceLabel.setLabelFor(sources);

        VBox headerBox = new VBox(6, typeBadge, title, originalTitle, metadataPills);
        detailBody = new VBox(12, headerBox, new VBox(6, synopsisLabel, synopsis),
                new VBox(6, sourceLabel, sources));
        VBox.setVgrow(detailBody.getChildren().get(1), Priority.ALWAYS);
        VBox.setVgrow(synopsis, Priority.ALWAYS);

        emptyDetail = emptyPlaceholder("Chọn một mục để xem chi tiết",
                "Bấm vào bộ phim, mùa hoặc tập ở danh sách bên trái để xem tóm tắt và nguồn phát.");
        VBox.setVgrow(emptyDetail, Priority.ALWAYS);

        StackPane detailsCard = new StackPane(emptyDetail, detailBody);
        detailsCard.getStyleClass().add("catalog-detail");

        var splitPane = new SplitPane(treeContainer, detailsCard);
        splitPane.setDividerPositions(0.40);
        setContent(splitPane);
        showDetails(null);
    }

    @Override
    public void focusSearch() {
        search.requestFocus();
        search.selectAll();
    }

    @Override
    public void refresh() {
        setLoading(true);
        controller.catalog(seriesList -> {
            catalog = seriesList;
            showTree();
        }, () -> setLoading(false));
    }

    // Lọc lại cây theo từ khóa và giữ nguyên trạng thái đóng mở cùng mục đang chọn nếu nó vẫn còn khớp.
    private void showTree() {
        String term = SearchText.normalize(search.getText());
        var matches = controller.filterCatalog(catalog, search.getText());
        Key selected = keyOf(tree.getSelectionModel().getSelectedItem());
        Set<Key> expanded = new HashSet<>();
        rememberExpanded(tree.getRoot(), expanded);
        var root = new TreeItem<CatalogNode>();
        root.setExpanded(true);
        for (SeriesNode node : matches) {
            root.getChildren().add(toItem(node, root, !term.isEmpty(), expanded));
        }
        tree.setRoot(root);

        int count = matches.size();
        resultCount.setText(term.isEmpty()
                ? count + " bộ phim"
                : count + " / " + catalog.size() + " bộ phim");
        boolean noMatches = count == 0;
        emptyTreeState.setVisible(noMatches);
        emptyTreeState.setManaged(noMatches);

        if (selected != null) {
            restoreSelection(root, selected);
        }
        if (tree.getSelectionModel().getSelectedItem() == null) {
            showDetails(null);
        }
    }

    // Chuyển đệ quy từng nút Composite sang TreeItem; lúc đang tìm kiếm thì tự bung nhánh dẫn tới kết quả.
    private static TreeItem<CatalogNode> toItem(CatalogNode node, TreeItem<CatalogNode> parent,
                                                boolean searching, Set<Key> expanded) {
        var item = new TreeItem<>(node);
        item.setExpanded(searching || expanded.contains(keyOf(node, parent)));
        for (CatalogNode child : node.children()) {
            item.getChildren().add(toItem(child, item, searching, expanded));
        }
        return item;
    }

    private static void rememberExpanded(TreeItem<CatalogNode> item, Set<Key> expanded) {
        if (item != null && item.isExpanded()) {
            expanded.add(keyOf(item));
            item.getChildren().forEach(child -> rememberExpanded(child, expanded));
        }
    }

    // Chọn lại đúng mục cũ nếu nó vẫn nằm trên nhánh đang mở sau khi lọc hoặc làm mới.
    private void restoreSelection(TreeItem<CatalogNode> item, Key selected) {
        if (selected.equals(keyOf(item))) {
            tree.getSelectionModel().select(item);
        } else if (item.isExpanded()) {
            item.getChildren().forEach(child -> restoreSelection(child, selected));
        }
    }

    private static Key keyOf(TreeItem<CatalogNode> item) {
        return item == null || item.getValue() == null ? null : keyOf(item.getValue(), item.getParent());
    }

    // Nhóm Chưa xếp mùa có id là null nên mình mượn id của bộ phim cha để phân biệt giữa các bộ khi giữ trạng thái mở cây.
    private static Key keyOf(CatalogNode node, TreeItem<CatalogNode> parent) {
        String id = node.id() == null ? parent.getValue().id() : node.id();
        return new Key(node.getClass(), id);
    }

    // Đổi nhãn, thẻ thông tin và nguồn phát tùy theo nút đang chọn là bộ, mùa hay tập (pattern matching trên sealed interface).
    private void showDetails(CatalogNode node) {
        boolean hasNode = node != null;
        emptyDetail.setVisible(!hasNode);
        emptyDetail.setManaged(!hasNode);
        detailBody.setVisible(hasNode);
        detailBody.setManaged(hasNode);
        if (!hasNode) {
            return;
        }

        title.setText(node.title());
        synopsis.setText(node.synopsis() == null || node.synopsis().isBlank() ? "Chưa có tóm tắt." : node.synopsis());
        metadataPills.getChildren().clear();
        originalTitle.setVisible(false);

        switch (node) {
            case SeriesNode series -> {
                setBadge("Bộ phim", "pill-sage");
                if (series.originalTitle() != null && !series.originalTitle().isBlank()) {
                    originalTitle.setText("Tên gốc: " + series.originalTitle());
                    originalTitle.setVisible(true);
                }
                metadataPills.getChildren().addAll(
                        pill("Mã: " + series.id(), "pill-neutral"),
                        pill(series.children().size() + " mùa", "pill-neutral"),
                        pill(ratingLabel(series.averageRating()), "pill-sage"));
                sources.setText("Chọn từng tập bên trái để xem nguồn phát.");
            }
            case SeasonNode season -> {
                setBadge("Mùa phim", "pill-honey");
                metadataPills.getChildren().addAll(
                        pill(season.children().size() + " tập", "pill-neutral"));
                sources.setText("Chọn từng tập bên trái để xem nguồn phát.");
            }
            case EpisodeNode episode -> {
                setBadge("Tập phim", "pill-neutral");
                metadataPills.getChildren().addAll(
                        pill("Mã: " + episode.id(), "pill-neutral"),
                        pill(durationLabel(episode.durationMs()), "pill-neutral"),
                        pill(ratingLabel(episode.averageRating()), "pill-sage"));
                sources.setText(episode.sources().isEmpty()
                        ? "Tập này chưa có nguồn YouTube hay Drive." : String.join("\n", episode.sources()));
            }
        }
    }

    private void setBadge(String text, String variantClass) {
        typeBadge.setText(text);
        typeBadge.getStyleClass().setAll("pill", variantClass);
    }

    private static String durationLabel(Long durationMs) {
        if (durationMs == null) {
            return "Chưa có thời lượng";
        }
        var duration = Duration.ofMillis(durationMs);
        return "%d phút %02d giây".formatted(duration.toMinutes(), duration.toSecondsPart());
    }

    private static String ratingLabel(Double average) {
        return average == null ? "Chưa có đánh giá"
                : String.format(Locale.forLanguageTag("vi-VN"), "Điểm TB %.2f / 10", average);
    }

    private static TextArea readOnlyArea() {
        var area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        return area;
    }

    // Khóa nhận diện một nút trên cây để giữ nguyên lựa chọn và nhánh đang mở sau khi làm mới.
    private record Key(Class<? extends CatalogNode> type, String id) {
    }
}
