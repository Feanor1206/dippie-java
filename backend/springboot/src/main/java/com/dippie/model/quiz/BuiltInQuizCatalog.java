package com.dippie.model.quiz;

import java.util.List;

// kho câu hỏi có sẵn tiếng Anh từ vựng (30 câu) và đố vui - khoa học (30 câu) chia đều 6 lớp học
public final class BuiltInQuizCatalog {

    private BuiltInQuizCatalog() {
    }

    public record SeedQuestion(
            String seedKey,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            QuizOptionKey correctOption,
            String explanation,
            QuizSubject subject,
            QuizGrade grade
    ) {
    }

    // 30 câu từ vựng tiếng Anh viết câu hỏi ngắn gọn để bé bấm nghe đọc chuẩn giọng en-US, giải thích tiếng Việt
    public static List<SeedQuestion> englishQuestions() {
        return List.of(
                // mẫu giáo (5)
                new SeedQuestion("en_mg_1", "What animal says 'Meow'?", "Cat", "Dog", "Duck", "Cow",
                        QuizOptionKey.A, "Cat là con mèo, kêu meo meo đó!", QuizSubject.TIENG_ANH, QuizGrade.MAU_GIAO),
                new SeedQuestion("en_mg_2", "What color is a ripe banana?", "Blue", "Yellow", "Purple", "Black",
                        QuizOptionKey.B, "Yellow là màu vàng giống quả chuối chín!", QuizSubject.TIENG_ANH, QuizGrade.MAU_GIAO),
                new SeedQuestion("en_mg_3", "Which number comes after four?", "Two", "Three", "Five", "One",
                        QuizOptionKey.C, "Đếm 1-2-3-4-5: sau four (4) là five (5).", QuizSubject.TIENG_ANH, QuizGrade.MAU_GIAO),
                new SeedQuestion("en_mg_4", "What animal says 'Woof'?", "Fish", "Bird", "Pig", "Dog",
                        QuizOptionKey.D, "Dog là chú chó con kêu gâu gâu!", QuizSubject.TIENG_ANH, QuizGrade.MAU_GIAO),
                new SeedQuestion("en_mg_5", "Which one is a red fruit?", "Apple", "Chair", "Book", "Car",
                        QuizOptionKey.A, "Apple là quả táo đỏ giòn ngọt.", QuizSubject.TIENG_ANH, QuizGrade.MAU_GIAO),

                // lớp 1 (5)
                new SeedQuestion("en_l1_1", "What color is the clear daytime sky?", "Green", "Blue", "Brown", "Pink",
                        QuizOptionKey.B, "Blue là màu xanh dương của bầu trời.", QuizSubject.TIENG_ANH, QuizGrade.LOP_1),
                new SeedQuestion("en_l1_2", "Which animal lives in water and swims?", "Lion", "Monkey", "Fish", "Rabbit",
                        QuizOptionKey.C, "Fish là con cá sống và bơi dưới nước.", QuizSubject.TIENG_ANH, QuizGrade.LOP_1),
                new SeedQuestion("en_l1_3", "What do you say in the morning to greet someone?", "Good morning", "Good night", "Goodbye", "Sorry",
                        QuizOptionKey.A, "Good morning nghĩa là chào buổi sáng.", QuizSubject.TIENG_ANH, QuizGrade.LOP_1),
                new SeedQuestion("en_l1_4", "Which item do you read at school?", "Shoe", "Plate", "Pillow", "Book",
                        QuizOptionKey.D, "Book là quyển sách dùng để đọc và học.", QuizSubject.TIENG_ANH, QuizGrade.LOP_1),
                new SeedQuestion("en_l1_5", "Who is your mom in English?", "Brother", "Mother", "Father", "Sister",
                        QuizOptionKey.B, "Mother là mẹ của bé.", QuizSubject.TIENG_ANH, QuizGrade.LOP_1),

                // lớp 2 (5)
                new SeedQuestion("en_l2_1", "Which animal is very big and has a long trunk?", "Mouse", "Elephant", "Frog", "Hen",
                        QuizOptionKey.B, "Elephant là con voi to lớn có vòi dài.", QuizSubject.TIENG_ANH, QuizGrade.LOP_2),
                new SeedQuestion("en_l2_2", "What is the weather like when the sun shines brightly?", "Rainy", "Snowy", "Sunny", "Windy",
                        QuizOptionKey.C, "Sunny nghĩa là trời có nắng đẹp.", QuizSubject.TIENG_ANH, QuizGrade.LOP_2),
                new SeedQuestion("en_l2_3", "What do you say when someone gives you a gift?", "Thank you", "Stand up", "Sit down", "Be quiet",
                        QuizOptionKey.A, "Thank you nghĩa là cảm ơn khi nhận quà.", QuizSubject.TIENG_ANH, QuizGrade.LOP_2),
                new SeedQuestion("en_l2_4", "Which body part helps you see?", "Ear", "Nose", "Hand", "Eye",
                        QuizOptionKey.D, "Eye là đôi mắt giúp bé nhìn thấy mọi vật.", QuizSubject.TIENG_ANH, QuizGrade.LOP_2),
                new SeedQuestion("en_l2_5", "Which one do you use to write with pencil lead?", "Ruler", "Eraser", "Pencil", "Bag",
                        QuizOptionKey.C, "Pencil là chiếc bút chì dùng để viết và vẽ.", QuizSubject.TIENG_ANH, QuizGrade.LOP_2),

                // lớp 3 (5)
                new SeedQuestion("en_l3_1", "What is the opposite of 'big'?", "Small", "Tall", "Fast", "Warm",
                        QuizOptionKey.A, "Big là to lớn, trái nghĩa là small (nhỏ bé).", QuizSubject.TIENG_ANH, QuizGrade.LOP_3),
                new SeedQuestion("en_l3_2", "Which season is usually the hottest?", "Winter", "Summer", "Autumn", "Spring",
                        QuizOptionKey.B, "Summer là mùa hè, thường có nắng nóng nhất năm.", QuizSubject.TIENG_ANH, QuizGrade.LOP_3),
                new SeedQuestion("en_l3_3", "Which day comes after Sunday?", "Friday", "Wednesday", "Monday", "Saturday",
                        QuizOptionKey.C, "Sau Chủ Nhật (Sunday) là Thứ Hai (Monday).", QuizSubject.TIENG_ANH, QuizGrade.LOP_3),
                new SeedQuestion("en_l3_4", "Where can you borrow many books at school?", "Kitchen", "Garden", "Garage", "Library",
                        QuizOptionKey.D, "Library là thư viện nơi có rất nhiều sách hay.", QuizSubject.TIENG_ANH, QuizGrade.LOP_3),
                new SeedQuestion("en_l3_5", "Which animal can fly high in the sky?", "Bird", "Turtle", "Whale", "Cow",
                        QuizOptionKey.A, "Bird là loài chim có cánh bay trên bầu trời.", QuizSubject.TIENG_ANH, QuizGrade.LOP_3),

                // lớp 4 (5)
                new SeedQuestion("en_l4_1", "Who helps sick people get better?", "Baker", "Doctor", "Driver", "Farmer",
                        QuizOptionKey.B, "Doctor là bác sĩ chữa bệnh cho mọi người.", QuizSubject.TIENG_ANH, QuizGrade.LOP_4),
                new SeedQuestion("en_l4_2", "What is the opposite of 'fast'?", "Loud", "Clean", "Slow", "Early",
                        QuizOptionKey.C, "Fast là nhanh, trái nghĩa là slow (chậm).", QuizSubject.TIENG_ANH, QuizGrade.LOP_4),
                new SeedQuestion("en_l4_3", "Which month is the last month of the year?", "January", "March", "August", "December",
                        QuizOptionKey.D, "December là tháng Mười Hai, tháng cuối năm.", QuizSubject.TIENG_ANH, QuizGrade.LOP_4),
                new SeedQuestion("en_l4_4", "Complete: She ___ to school every morning.", "goes", "go", "going", "gone",
                        QuizOptionKey.A, "Chủ ngữ She ở thì hiện tại đơn dùng động từ thêm es: goes.", QuizSubject.TIENG_ANH, QuizGrade.LOP_4),
                new SeedQuestion("en_l4_5", "Which place is where sick patients stay for care?", "Market", "Hospital", "Cinema", "Park",
                        QuizOptionKey.B, "Hospital là bệnh viện.", QuizSubject.TIENG_ANH, QuizGrade.LOP_4),

                // lớp 5 (5)
                new SeedQuestion("en_l5_1", "What is the past tense of 'go'?", "Goed", "Going", "Went", "Goes",
                        QuizOptionKey.C, "Quá khứ bất quy tắc của động từ go là went.", QuizSubject.TIENG_ANH, QuizGrade.LOP_5),
                new SeedQuestion("en_l5_2", "Complete: An elephant is ___ than a cat.", "big", "biggest", "more big", "bigger",
                        QuizOptionKey.D, "So sánh hơn với tính từ ngắn big gấp đôi phụ âm thành bigger.", QuizSubject.TIENG_ANH, QuizGrade.LOP_5),
                new SeedQuestion("en_l5_3", "Which word means keeping nature clean and green?", "Environment", "Furniture", "Holiday", "Receipt",
                        QuizOptionKey.A, "Environment nghĩa là môi trường sống xung quanh ta.", QuizSubject.TIENG_ANH, QuizGrade.LOP_5),
                new SeedQuestion("en_l5_4", "Where can you see ancient artifacts and art?", "Stadium", "Museum", "Bakery", "Airport",
                        QuizOptionKey.B, "Museum là bảo tàng trưng bày hiện vật và nghệ thuật.", QuizSubject.TIENG_ANH, QuizGrade.LOP_5),
                new SeedQuestion("en_l5_5", "Which planet is known as the Red Planet?", "Venus", "Saturn", "Mars", "Neptune",
                        QuizOptionKey.C, "Mars là Sao Hỏa, nổi tiếng với bề mặt màu đỏ cam.", QuizSubject.TIENG_ANH, QuizGrade.LOP_5)
        );
    }

    // 30 câu đố vui và khoa học tự nhiên bằng tiếng Việt gần gũi với trẻ em từ mẫu giáo đến lớp 5
    public static List<SeedQuestion> scienceAndRiddleQuestions() {
        return List.of(
                // mẫu giáo (5)
                new SeedQuestion("sr_mg_1", "Con gì kêu 'ò ó o' gọi mọi người thức dậy mỗi sáng?", "Con gà trống", "Con vịt", "Con mèo", "Con cá",
                        QuizOptionKey.A, "Gà trống hay gáy ò ó o vào buổi sáng sớm!", QuizSubject.DO_VUI, QuizGrade.MAU_GIAO),
                new SeedQuestion("sr_mg_2", "Ban đêm trên bầu trời thường có gì tỏa sáng?", "Cầu vồng", "Mặt trăng và sao", "Mặt trời", "Mây hồng",
                        QuizOptionKey.B, "Ban đêm trời tối nên ta thấy mặt trăng và các ngôi sao lấp lánh.", QuizSubject.KHOA_HOC, QuizGrade.MAU_GIAO),
                new SeedQuestion("sr_mg_3", "Bé dùng bộ phận nào trên cơ thể để nghe tiếng hát?", "Cái mũi", "Bàn tay", "Đôi tai", "Cái miệng",
                        QuizOptionKey.C, "Đôi tai giúp bé nghe âm thanh và tiếng nhạc.", QuizSubject.KHOA_HOC, QuizGrade.MAU_GIAO),
                new SeedQuestion("sr_mg_4", "Con vật nào có cái vòi rất dài để hút nước?", "Con thỏ", "Con gà", "Con cừu", "Con voi",
                        QuizOptionKey.D, "Chú voi có chiếc vòi vừa dài vừa khỏe!", QuizSubject.DO_VUI, QuizGrade.MAU_GIAO),
                new SeedQuestion("sr_mg_5", "Cây xanh cần gì để lớn lên khỏe mạnh?", "Nước và ánh nắng", "Kẹo ngọt", "Đồ chơi", "Băng keo",
                        QuizOptionKey.A, "Cây xanh cần nước tưới và ánh nắng mặt trời mỗi ngày.", QuizSubject.KHOA_HOC, QuizGrade.MAU_GIAO),

                // lớp 1 (5)
                new SeedQuestion("sr_l1_1", "Một tuần lễ có tất cả mấy ngày?", "5 ngày", "7 ngày", "10 ngày", "12 ngày",
                        QuizOptionKey.B, "Một tuần có 7 ngày từ Thứ Hai đến Chủ Nhật.", QuizSubject.DO_VUI, QuizGrade.LOP_1),
                new SeedQuestion("sr_l1_2", "Con gì đi đâu cũng mang theo ngôi nhà trên lưng?", "Con kiến", "Con ong", "Con ốc sên", "Con bướm",
                        QuizOptionKey.C, "Chiếc vỏ xoắn trên lưng chính là ngôi nhà của ốc sên đó!", QuizSubject.DO_VUI, QuizGrade.LOP_1),
                new SeedQuestion("sr_l1_3", "Nước cho vào ngăn đá tủ lạnh sẽ biến thành gì?", "Nước đá", "Hơi nước", "Dầu ăn", "Cát trắng",
                        QuizOptionKey.A, "Khi lạnh dưới 0 độ C, nước đông lại thành nước đá.", QuizSubject.KHOA_HOC, QuizGrade.LOP_1),
                new SeedQuestion("sr_l1_4", "Buổi sáng mặt trời mọc ở hướng nào?", "Hướng Tây", "Hướng Nam", "Hướng Bắc", "Hướng Đông",
                        QuizOptionKey.D, "Mặt trời luôn mọc ở hướng Đông và lặn ở hướng Tây.", QuizSubject.KHOA_HOC, QuizGrade.LOP_1),
                new SeedQuestion("sr_l1_5", "Loài vật nào sống dưới nước và thở bằng mang?", "Con chó", "Con cá", "Con chim", "Con mèo",
                        QuizOptionKey.B, "Cá có mang hai bên đầu để lấy oxy trong nước.", QuizSubject.KHOA_HOC, QuizGrade.LOP_1),

                // lớp 2 (5)
                new SeedQuestion("sr_l2_1", "Một năm có bao nhiêu tháng?", "10 tháng", "11 tháng", "12 tháng", "14 tháng",
                        QuizOptionKey.C, "Một năm có đúng 12 tháng từ tháng 1 đến tháng 12.", QuizSubject.DO_VUI, QuizGrade.LOP_2),
                new SeedQuestion("sr_l2_2", "Bộ phận nào của cây hút nước và chất dinh dưỡng từ đất?", "Rễ cây", "Cánh hoa", "Quả chín", "Ngọn cây",
                        QuizOptionKey.A, "Rễ cây bám sâu trong đất để hút nước nuôi cả cây.", QuizSubject.KHOA_HOC, QuizGrade.LOP_2),
                new SeedQuestion("sr_l2_3", "Cầu vồng 7 màu thường xuất hiện vào lúc nào?", "Giữa đêm khuya", "Sau cơn mưa có nắng", "Khi trời đầy sương", "Trong phòng tối",
                        QuizOptionKey.B, "Ánh nắng chiếu qua những hạt mưa nhỏ tạo nên cầu vồng rực rỡ.", QuizSubject.KHOA_HOC, QuizGrade.LOP_2),
                new SeedQuestion("sr_l2_4", "Con vật nào đổi màu da để trốn kẻ thù rất giỏi?", "Con bò", "Con vịt", "Con mèo", "Tắc kè hoa",
                        QuizOptionKey.D, "Tắc kè hoa có thể đổi màu da giống cành lá xung quanh.", QuizSubject.KHOA_HOC, QuizGrade.LOP_2),
                new SeedQuestion("sr_l2_5", "Đố vui: Cái gì càng lau thì nó lại càng ướt?", "Cái khăn lau", "Cái chổi", "Cái bàn", "Cái ghế",
                        QuizOptionKey.A, "Khăn lau thấm nước từ đồ vật nên càng lau càng ướt!", QuizSubject.DO_VUI, QuizGrade.LOP_2),

                // lớp 3 (5)
                new SeedQuestion("sr_l3_1", "Trái Đất của chúng ta quay quanh ngôi sao nào?", "Mặt Trăng", "Mặt Trời", "Sao Hỏa", "Sao Kim",
                        QuizOptionKey.B, "Trái Đất và các hành tinh đều quay quanh Mặt Trời.", QuizSubject.KHOA_HOC, QuizGrade.LOP_3),
                new SeedQuestion("sr_l3_2", "Cơ quan nào đập liên tục để bơm máu đi khắp cơ thể?", "Dạ dày", "Đôi mắt", "Trái tim", "Bàn chân",
                        QuizOptionKey.C, "Trái tim đập suốt ngày đêm để đưa máu nuôi cơ thể.", QuizSubject.KHOA_HOC, QuizGrade.LOP_3),
                new SeedQuestion("sr_l3_3", "Nước tinh khiết sôi ở nhiệt độ bao nhiêu độ C?", "100 độ C", "50 độ C", "10 độ C", "0 độ C",
                        QuizOptionKey.A, "Ở áp suất thường, nước sôi ở 100 độ C và đóng băng ở 0 độ C.", QuizSubject.KHOA_HOC, QuizGrade.LOP_3),
                new SeedQuestion("sr_l3_4", "Loài chim nào không biết bay nhưng bơi lội rất giỏi?", "Chim sẻ", "Chim đại bàng", "Chim bồ câu", "Chim cánh cụt",
                        QuizOptionKey.D, "Chim cánh cụt dùng đôi cánh như mái chèo để bơi cực nhanh.", QuizSubject.KHOA_HOC, QuizGrade.LOP_3),
                new SeedQuestion("sr_l3_5", "Cây xanh dùng ánh nắng tạo ra thức ăn nhờ quá trình nào?", "Quang hợp", "Bay hơi", "Ngưng tụ", "Đóng băng",
                        QuizOptionKey.A, "Lá cây thực hiện quang hợp nhờ ánh sáng mặt trời.", QuizSubject.KHOA_HOC, QuizGrade.LOP_3),

                // lớp 4 (5)
                new SeedQuestion("sr_l4_1", "Trong không khí quanh ta, chất khí nào chiếm tỷ lệ nhiều nhất?", "Khí Oxy", "Khí Nitơ", "Khí Cacbonic", "Khí Heli",
                        QuizOptionKey.B, "Khí Nitơ chiếm khoảng 78% không khí, còn Oxy chiếm khoảng 21%.", QuizSubject.KHOA_HOC, QuizGrade.LOP_4),
                new SeedQuestion("sr_l4_2", "Âm thanh truyền đi nhanh nhất trong môi trường nào?", "Không khí", "Nước lỏng", "Chất rắn", "Chân không",
                        QuizOptionKey.C, "Các hạt trong chất rắn xếp sát nhau nên âm thanh truyền nhanh nhất.", QuizSubject.KHOA_HOC, QuizGrade.LOP_4),
                new SeedQuestion("sr_l4_3", "Hành tinh nào có kích thước lớn nhất trong Hệ Mặt Trời?", "Sao Mộc", "Sao Thủy", "Trái Đất", "Sao Hỏa",
                        QuizOptionKey.A, "Sao Mộc là hành tinh khổng lồ lớn nhất Hệ Mặt Trời.", QuizSubject.KHOA_HOC, QuizGrade.LOP_4),
                new SeedQuestion("sr_l4_4", "Chất nào tạo nên màu xanh lục cho lá cây?", "Tinh bột", "Canxi", "Nước muối", "Diệp lục",
                        QuizOptionKey.D, "Chất diệp lục giúp lá cây có màu xanh và hấp thụ ánh nắng.", QuizSubject.KHOA_HOC, QuizGrade.LOP_4),
                new SeedQuestion("sr_l4_5", "Đố vui: Cái gì có cổ mà không có đầu, có hai tay mà không có chân?", "Cái áo", "Cái đồng hồ", "Cái ly", "Cái lược",
                        QuizOptionKey.A, "Chiếc áo có cổ áo và hai tay áo đó bé!", QuizSubject.DO_VUI, QuizGrade.LOP_4),

                // lớp 5 (5)
                new SeedQuestion("sr_l5_1", "Trái Đất mất khoảng bao lâu để quay đủ một vòng quanh Mặt Trời?", "24 giờ", "30 ngày", "365 ngày", "100 ngày",
                        QuizOptionKey.C, "Một vòng Trái Đất quanh Mặt Trời mất khoảng 365 ngày, tức 1 năm.", QuizSubject.KHOA_HOC, QuizGrade.LOP_5),
                new SeedQuestion("sr_l5_2", "Đỉnh núi cao nhất Việt Nam nằm ở dãy Hoàng Liên Sơn tên là gì?", "Fansipan", "Bà Đen", "Langbiang", "Ngọc Linh",
                        QuizOptionKey.A, "Đỉnh Fansipan cao 3.143m được mệnh danh là nóc nhà Đông Dương.", QuizSubject.KHOA_HOC, QuizGrade.LOP_5),
                new SeedQuestion("sr_l5_3", "Nam châm hút mạnh nhất loại vật liệu nào sau đây?", "Gỗ khô", "Sắt", "Nhựa dẻo", "Thủy tinh",
                        QuizOptionKey.B, "Nam châm hút mạnh các vật làm từ sắt, thép, niken.", QuizSubject.KHOA_HOC, QuizGrade.LOP_5),
                new SeedQuestion("sr_l5_4", "Ban ngày khi quang hợp, cây xanh nhả ra khí gì giúp con người thở?", "Khí Nitơ", "Khí Metan", "Khí Cacbonic", "Khí Oxy",
                        QuizOptionKey.D, "Cây xanh hấp thụ khí Cacbonic và nhả ra khí Oxy trong lành.", QuizSubject.KHOA_HOC, QuizGrade.LOP_5),
                new SeedQuestion("sr_l5_5", "Đố vui: Tháng nào trong năm có 28 ngày?", "Tất cả 12 tháng", "Chỉ tháng 2", "Chỉ tháng 1", "Chỉ tháng 12",
                        QuizOptionKey.A, "Tháng nào cũng có ít nhất 28 ngày, riêng tháng 2 có đúng 28 hoặc 29 ngày!", QuizSubject.DO_VUI, QuizGrade.LOP_5)
        );
    }
}
