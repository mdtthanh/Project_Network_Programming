import javax.swing.*;
import java.awt.*;

public class HomePage extends JDialog {
    private JButton TaoPhongButton;
    private JPanel Home;
    private JTextField nhapTin;
    private JButton ChoiNhanhButton;
    private JButton VaoPhongButton;
    private JButton TimPhongButton;
    private JButton BanBeButton;
    private JButton ThoatButton;
    private JButton ChoiMayButton;
    private JButton DangxuatButton;
    private JButton XepHangButton;
    private JLabel avatar;
    private JLabel NickName;
    private JLabel SoVanChoi;
    private JLabel SoVanThang;
    private JLabel SoVanHoa;
    private JLabel TiLeThang;
    private JLabel Diem;
    private JLabel Hang;
    private JLabel NickNameVal;
    private JLabel NumWin;
    private JLabel NumHoa;
    private JLabel TiLe;
    private JLabel Point;
    private JLabel Rank;
    private JButton GuiTinButton;
    private JLabel NumPlay;
    private JTextArea TinTuc;

    public HomePage(JFrame parent){
        super(parent);
        setTitle("Trò chơi Caro");
        setContentPane(Home);
        setMinimumSize(new Dimension(350, 450));
        setModal(true);
        setLocationRelativeTo(parent);
        setVisible(true);
    }
    
    public static void main(String[] args) {
        HomePage myHomepage = new HomePage( null);
    }
}
