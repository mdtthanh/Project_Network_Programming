/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controller;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import model.User;
import model.Response;

/**
 *
 * @author Admin
 */
public class SocketHandle implements Runnable {
  private BufferedWriter os;
  private BufferedReader is;
  private Socket socketOfClient;
//  private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);   
  
  public List<User> getListUser(String[] message){
    Pattern p = Pattern.compile("id=(\\d+),username=([a-zA-Z0-9]+),avatar=([a-zA-Z0-9/\\.]+),is_online=([a-z]+),is_playing=([a-z]+)");
    Matcher m;
    List<User> friend = new ArrayList<>();
    for (String message1 : message) {
      m = p.matcher(message1);
      if(m.find())
        friend.add(new User(
          Integer.parseInt(m.group(1)),
          m.group(2),
          m.group(3),
          Boolean.parseBoolean(m.group(4)),
          Boolean.parseBoolean(m.group(5))
        ));
    }
    return friend;
  }
  
  public List<User> getListRank(String data){
    String[] splitter = data.split(";");
    List<User> player = new ArrayList<>();
    Pattern pattern = Pattern.compile("id=(\\d+),username=([a-zA-Z0-9]+),avatar=([a-zA-Z0-9/\\.]+),win=(\\d+),loss=(\\d+),points=(-?\\d+)");
    Matcher m;
    for (String splitter1 : splitter) {
      m = pattern.matcher(splitter1);
      if(m.find()) {
        player.add(new User(
          Integer.parseInt(m.group(1)),
          m.group(2),
          m.group(3),
          Integer.parseInt(m.group(4)),
          Integer.parseInt(m.group(5)), 
          Integer.parseInt(m.group(6))
        )
        );
      }
    }
    return player;
  }
  
  // data: id=...,username=...,password=...,avatar=...,game=...,win=...,draw=...,loss=...,points=...,rank=...
  public User getUserFromString(String data){
    Pattern pattern = Pattern.compile(
      "id=(\\d+),username=([a-zA-Z0-9]+),password=([a-zA-Z0-9]+),avatar=([a-zA-Z0-9/\\.]+),"
          + "game=(\\d+),win=(\\d+),draw=(\\d+),loss=(\\d+),points=(\\d+),rank=(\\d+),is_online=([a-z]+),is_playing=([a-z]+)"
    );
    Matcher m = pattern.matcher(data);
    m.find();
    return new User(
      Integer.parseInt(m.group(1)), 
      m.group(2), 
      m.group(3), 
      m.group(4), 
      Integer.parseInt(m.group(5)), 
      Integer.parseInt(m.group(6)), 
      Integer.parseInt(m.group(7)), 
      Integer.parseInt(m.group(8)), 
      Integer.parseInt(m.group(9)), 
      Integer.parseInt(m.group(10)),
      Boolean.parseBoolean(m.group(11)),
      Boolean.parseBoolean(m.group(12))
    );
  }
  
  @Override
  public void run() {
    try {
      // G???i y??u c???u k???t n???i t???i Server ??ang l???ng nghe
//      socketOfClient = new Socket("0.tcp.ap.ngrok.io", 10874);
      socketOfClient = new Socket("127.0.0.1", 12121);
      socketOfClient.setKeepAlive(true);
      Client.isKeepAlive = true;
      System.out.println("K???t n???i th??nh c??ng!");
      
      // T???o lu???ng ?????u ra t???i client (G???i d??? li???u t???i server)
      os = new BufferedWriter(new OutputStreamWriter(socketOfClient.getOutputStream()));
      // Lu???ng ?????u v??o t???i Client (Nh???n d??? li???u t??? server).
      is = new BufferedReader(new InputStreamReader(socketOfClient.getInputStream()));
      String message;
      Response res;
     
      
      while (true) {
        // Nh???n response t??? server
        message = is.readLine();
        if (message == null) {
          System.out.println("Server crash...");
          Client.isKeepAlive = false;
          Client.serverCrash();
          break;
        }
        System.out.println("Server response: " + message);
        res = new Response(message);

        if(res.getState().equals("keep_alive")) {
          write("KEEP_ALIVE#0#0#");
        }
        
        if(res.getState().equals("server_ok")) {
          System.out.println("SERVER 200/OK");
        }

        /* ---------------------------------------------------------------------------------- */
        /*                                AUTHENTICATION                                      */
        /* ---------------------------------------------------------------------------------- */
        
        // ????ng nh???p th??nh c??ng
        if(res.getState().equals("login_success") || res.getState().equals("register_success")){
          System.out.println("????ng nh???p th??nh c??ng");
          Client.closeAllViews();
          User user = getUserFromString(res.getData());
          Client.user = user;

          // G???i request keep-alive cho server ????? ki???m tra k???t n???i ?????nh k?? m???i 30s
          new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
              try {
                write("KEEP_ALIVE#0#0#");
              } catch (IOException ex) {
                Client.isKeepAlive = false;
              }
            }
          }, 0, 60000);
          Client.openView(Client.View.HOMEPAGE);
        }     
        
        // Th??ng tin t??i kho???n sai
        if(res.getState().equals("account_incorrect")){
          Client.closeView(Client.View.GAMENOTICE);
          Pattern pattern = Pattern.compile("username=([a-zA-Z0-9]+),password=([a-zA-Z0-9]+)");
          Matcher m = pattern.matcher(res.getData());
          m.find();
          Client.openView(Client.View.LOGIN, m.group(1), m.group(2));
          Client.loginFrm.showError("Username ho???c m???t kh???u kh??ng ch??nh x??c");
        }
        
        // T??i kho???n ???? ????ng nh???p ??? n??i kh??c
        if(res.getState().equals("login_duplicate")){
          System.out.println("???? ????ng nh???p");
          Client.closeView(Client.View.GAMENOTICE);          
          Pattern pattern = Pattern.compile("username=([a-zA-Z0-9]+),password=([a-zA-Z0-9]+)");
          Matcher m = pattern.matcher(res.getData());
          m.find();
          Client.openView(Client.View.LOGIN, m.group(1), m.group(2));
          Client.loginFrm.showError("T??i kho???n ???? ????ng nh???p ??? n??i kh??c");
        }       
       
        // X??? l?? register tr??ng t??n
        if(res.getState().equals("username_duplicate")){
          Client.closeAllViews();
          Client.openView(Client.View.REGISTER);
          JOptionPane.showMessageDialog(Client.registerFrm, "Username ???? ???????c s??? d???ng");
        }
        
        // X??? l?? t??i kho???n ho???c m???t kh???u kh??ng h???p l??? 
        if(res.getState().equals("account_invalid")){
          Client.closeAllViews();
          Client.openView(Client.View.REGISTER);
          JOptionPane.showMessageDialog(Client.registerFrm, "T??i kho???n v?? m???t kh???u kh??ng h???p l???");
        }
        
        
        /* ---------------------------------------------------------------------------------- */
        /*                                      CHAT                                          */
        /* ---------------------------------------------------------------------------------- */
 
        // X??? l?? chat global
        if(res.getState().equals("chat_global")){
          Client.homePageFrm.addMessage(res.getData());
        }
        
        // X??? l?? chat trong game
        if(res.getState().equals("chat_local")){
          Client.gameClientFrm.addMessage(res.getData());
        }
        
        // X??? l?? chat kh??ng h???p l??? 
        if(res.getState().equals("chat_fail")){
          if(Client.homePageFrm != null){
            Client.homePageFrm.addMessage(res.getData());
          }
          else {
            Client.gameClientFrm.addMessage(res.getData());
          }
        }
        
        /* ---------------------------------------------------------------------------------- */
        /*                                      ROOM                                          */
        /* ---------------------------------------------------------------------------------- */
        
                
        // T???o ph??ng v?? server tr??? v??? t??n ph??ng
        if(res.getState().equals("game_created")){
          Client.closeAllViews();
          Client.openView(Client.View.WAITINGROOM);
          Pattern pattern = Pattern.compile("game_id=(\\d+)(,password=([a-zA-Z0-9]+))?");
          Matcher m = pattern.matcher(res.getData());
          m.find();
          Client.waitingRoomFrm.game_id = Integer.parseInt(m.group(1));
          Client.waitingRoomFrm.setRoomName(m.group(1));
          Client.waitingRoomFrm.setRoomPassword("M???t kh???u ph??ng: " + (m.group(2) == null ? "kh??ng c??" : m.group(3)));
        }
        
        // X??? l?? l???y danh s??ch ph??ng
        if(res.getState().equals("game_list")){
          Vector<String> rooms = new Vector<>();
          Vector<String> passwords = new Vector<>();
          String[] splitter = res.getData().split(";");
          Pattern p = Pattern.compile("game_id=(\\d+),password=([a-zA-Z0-9]+)?,num_move=(\\d+),player1_id=(\\d+),player2_id=(\\d+)");
          Matcher m;
          for (String splitter1 : splitter) {
            m = p.matcher(splitter1);
            if(m.find()) {
              rooms.add("Ph??ng " + m.group(1));
              passwords.add(m.group(2));
              System.out.println("Room: " + m.group(1) + " - Password: " + m.group(2));
            }
          }
          Client.roomListFrm.updateRoomList(rooms, passwords);
        }
        
        // X??? l?? v??o ph??ng ???? ????? ng?????i ch??i 
        if(res.getState().equals("game_full")){
          Client.closeAllViews();
          Client.openView(Client.View.HOMEPAGE);
          JOptionPane.showMessageDialog(Client.homePageFrm, "Ph??ng ch??i ???? ????? 2 ng?????i ch??i");
        }
        
        // X??? l?? ???? v??o 1 ph??ng th?? kh??ng th??? v??o c??c ph??ng kh??c
        if(res.getState().equals("game_playing")){
          JOptionPane.showMessageDialog(Client.gameClientFrm, "B???n ???? v??o ph??ng");
        }
        
        // X??? l?? kh??ng t??m th???y ph??ng trong ch???c n??ng v??o ph??ng
        if(res.getState().equals("game_null")){
          Client.closeAllViews();
          Client.openView(Client.View.HOMEPAGE);
          JOptionPane.showMessageDialog(Client.homePageFrm, "Kh??ng t??m th???y ph??ng");
        }
    
        // X??? l?? ph??ng c?? m???t kh???u sai
        if(res.getState().equals("game_password_incorrect")){
          Client.closeAllViews();
          Client.openView(Client.View.HOMEPAGE);
          JOptionPane.showMessageDialog(Client.homePageFrm, "M???t kh???u ph??ng sai");
        }
     
        // X??? l?? v??o ph??ng. data: game_id=...,is_start=...,ip=...,id=...,username=...,password=...,avatar=...,game=...,win=...,draw=...,loss=...,points=...,rank=...
        if(res.getState().equals("game_joined")){
          String[] splitter = res.getData().split(";");
          Pattern p = Pattern.compile(
            "game_id=(\\d+),is_start=(\\d+),ip=([\\.\\d]+),"
            + "id=(\\d+),username=([a-zA-Z0-9]+),avatar=([a-zA-Z0-9/\\.]+),game=(\\d+),"
            + "win=(\\d+),draw=(\\d+),loss=(\\d+),points=(\\d+),rank=(\\d+)"
          );
          Matcher m;
          int opponentID, roomID = 0, isStart = 0;
          String competitorIP = "0.0.0.0";
          User competitor = new User(0, "");
          for (String splitter1 : splitter) {
            m = p.matcher(splitter1);
            if(m.find()) {
              opponentID = Integer.parseInt(m.group(4));
              
              if(opponentID != Client.user.getID()) {
                roomID = Integer.parseInt(m.group(1));
                isStart = Integer.parseInt(m.group(2));
                competitorIP = m.group(3);
                competitor = new User(
                  opponentID, m.group(5), "", m.group(6),
                  Integer.parseInt(m.group(7)), Integer.parseInt(m.group(8)),
                  Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10)),
                  Integer.parseInt(m.group(11)), Integer.parseInt(m.group(12))
                );
              }
            }
          }
          System.out.println("V??o ph??ng");
          
          if(Client.findRoomFrm != null){
            Client.findRoomFrm.showFindedRoom();
            try {
              Thread.sleep(3000);
            } catch (InterruptedException ex) {
              JOptionPane.showMessageDialog(Client.findRoomFrm, "L???i khi sleep thread");
            }
          } 
          else if(Client.waitingRoomFrm != null){
            Client.waitingRoomFrm.showFindedCompetitor();
            try {
              Thread.sleep(3000);
            } catch (InterruptedException ex) {
              JOptionPane.showMessageDialog(Client.waitingRoomFrm, "L???i khi sleep thread");
            }
          }
          Client.closeAllViews();
          System.out.println("???? v??o ph??ng: " + roomID);
          Client.user.setIsPlaying(true);
          
          Client.openView(Client.View.GAMECLIENT, competitor, roomID, isStart, competitorIP);
          Client.gameClientFrm.newgame();
        }
        
        // X??? l?? r???i ph??ng
        if(res.getState().equals("game_quit")){
          Client.gameClientFrm.stopTimer();
          Client.closeAllViews();
          Client.openView(Client.View.GAMENOTICE, "?????i th??? ???? tho??t kh???i ph??ng", "??ang tr??? v??? trang ch???");
          Client.user.updateAchieve("win");
          Thread.sleep(2000);       
          Client.closeAllViews();
          Client.openView(Client.View.HOMEPAGE);
        }

        
        /* ---------------------------------------------------------------------------------- */
        /*                                      FRIEND                                        */
        /* ---------------------------------------------------------------------------------- */
        
                        
        // X??? l?? hi???n th??? th??ng tin ?????i th??? l?? b???n b??/kh??ng
        if(res.getState().equals("friend_check")){
          if(Client.competitorInfoFrm != null){
            Pattern p = Pattern.compile("is_friend=(\\d+)");
            Matcher m = p.matcher(res.getData());
            m.find();
            String isFriend = m.group(1);
            Client.competitorInfoFrm.checkFriend((isFriend.equals("1")));
          }
        }
        
        // X??? l?? danh s??ch b???n b?? 
        if(res.getState().equals("friend_list")){
          if(Client.friendListFrm != null){
            String[] splitter = res.getData().split(";");
            Client.friendListFrm.updateFriendList(getListUser(splitter));
          }
        }
        
        // X??? l?? y??u c???u k???t b???n t???i
        if(res.getState().equals("friend_request")){
          Pattern p = Pattern.compile("player_id=(\\d+),username=([a-zA-Z0-9]+)");
          Matcher m = p.matcher(res.getData());
          m.find();
          int ID = Integer.parseInt(m.group(1));
          String username = m.group(2);
          Client.openView(Client.View.FRIENDREQUEST, "friend", ID, username);
        }
        
        // X??? l?? xem rank
        if(res.getState().equals("rank")){
          if(Client.rankFrm != null){
            Client.rankFrm.setDataToTable(getListRank(res.getData()));
          }
        }
        
        /* ---------------------------------------------------------------------------------- */
        /*                                CHALLENGE REQUEST                                   */
        /* ---------------------------------------------------------------------------------- */

        // X??? l?? khi nh???n ???????c y??u c???u th??ch ?????u
        if(res.getState().equals("duel_request")){
          Pattern p = Pattern.compile("player_id=(\\d+),username=([a-zA-Z0-9]+)");
          Matcher m = p.matcher(res.getData());
          m.find();
          int ID = Integer.parseInt(m.group(1));
          String username = m.group(2);
          Client.openView(Client.View.FRIENDREQUEST, "duel", ID, username);
        }
        
        // X??? l?? kh??ng ?????ng ?? th??ch ?????u
        if(res.getState().equals("duel_rejected")){
          Client.closeAllViews();
          Client.openView(Client.View.HOMEPAGE);
          JOptionPane.showMessageDialog(Client.homePageFrm, "?????i th??? kh??ng ?????ng ?? th??ch ?????u");
        }
        
        // X??? l?? ?????ng ?? th??ch ?????u
        if(res.getState().equals("duel_accepted")){
          String[] splitter = res.getData().split(";");
          Pattern p = Pattern.compile(
            "game_id=(\\d+),is_start=(\\d+),ip=([\\.\\d]+),"
            + "id=(\\d+),username=([a-zA-Z0-9]+),avatar=([a-zA-Z0-9/\\.]+),game=(\\d+),"
            + "win=(\\d+),draw=(\\d+),loss=(\\d+),points=(\\d+),rank=(\\d+)"
          );
          Matcher m;
          int opponentID, roomID = 0, isStart = 0;
          String competitorIP = "0.0.0.0";
          User competitor = new User(0, "");
          for (String splitter1 : splitter) {
            m = p.matcher(splitter1);
            if(m.find()) {
              opponentID = Integer.parseInt(m.group(4));
              
              if(opponentID != Client.user.getID()) {
                roomID = Integer.parseInt(m.group(1));
                isStart = Integer.parseInt(m.group(2));
                competitorIP = m.group(3);
                competitor = new User(
                  opponentID, m.group(5), "", m.group(6),
                  Integer.parseInt(m.group(7)), Integer.parseInt(m.group(8)),
                  Integer.parseInt(m.group(9)), Integer.parseInt(m.group(10)),
                  Integer.parseInt(m.group(11)), Integer.parseInt(m.group(12))
                );
              }
            }
          }

          Client.closeAllViews();
          System.out.println("???? v??o ph??ng: " + roomID);
          Client.openView(Client.View.GAMECLIENT, competitor, roomID, isStart, competitorIP);
          Client.gameClientFrm.newgame();
        }


        /* ---------------------------------------------------------------------------------- */
        /*                                     GAME CARO                                      */
        /* ---------------------------------------------------------------------------------- */

        
        // X??? l?? ????nh m???t n?????c trong v??n ch??i
        if(res.getState().equals("caro")){
          Pattern p = Pattern.compile("x=(\\d+),y=(\\d+)");
          Matcher m = p.matcher(res.getData());
          m.find();
          Client.gameClientFrm.addCompetitorMove(m.group(1), m.group(2));
        }

        if(res.getState().equals("draw_request")){
          Client.gameClientFrm.showDrawRequest();
        }

        if(res.getState().equals("draw_refuse")){
          if(Client.gameNoticeFrm != null) Client.closeView(Client.View.GAMENOTICE);
          Client.gameClientFrm.displayDrawRefuse();
        }

        if(res.getState().equals("new_game")){
          System.out.println("New game");
          Thread.sleep(2000);
          Client.gameClientFrm.updateNumberOfGame();
          Client.closeView(Client.View.GAMENOTICE);
          Client.gameClientFrm.newgame();
        }

        if(res.getState().equals("draw")){
          System.out.println("Draw game");
          Client.closeView(Client.View.GAMENOTICE);
          Client.openView(Client.View.GAMENOTICE, "V??n ch??i h??a", "V??n ch??i m???i dang ???????c thi???t l???p");
          Client.gameClientFrm.displayDrawGame();
          Thread.sleep(2000);
          Client.gameClientFrm.updateNumberOfGame();
          Client.closeView(Client.View.GAMENOTICE);
          Client.gameClientFrm.newgame();
        }
        
        if(res.getState().equals("timeout")){
          Pattern p = Pattern.compile("game_id=(\\d+),opponent_id=(\\d+)");
          Matcher m = p.matcher(res.getData());
          m.find();
          int roomID = Integer.parseInt(m.group(1));
          int opponentID = Integer.parseInt(m.group(2));
          Client.gameClientFrm.increaseWinMatchToUser();

          Client.openView(Client.View.GAMENOTICE, "B???n ???? th???ng do ?????i th??? qu?? th???i gian", "??ang thi???t l???p v??n ch??i m???i");
          Thread.sleep(2000);
          Client.closeView(Client.View.GAMENOTICE);
          Client.user.updateAchieve("win");
          Client.socketHandle.write(
            Client.socketHandle.requestify(
              "GAME_FINISH", 0, 
              "game_id=" + roomID + "&player_id=" + Client.user.getID() + "&opponent_id=" + opponentID + "&x=-1&y=-1&result=1&type=timeout", 
              ""
            )
          );
        }
        
        
        /* ---------------------------------------------------------------------------------- */
        /*                                        OTHER                                       */
        /* ---------------------------------------------------------------------------------- */
        
                
        if(res.getState().equals("updating")) {
          Client.openView(Client.View.GAMENOTICE, "??ang c???p nh???t d??? li???u m???i nh???t", "Vui l??ng ch???");
        }
        
        if(res.getState().equals("updated")) {
          Thread.sleep(1000);
          Client.closeView(Client.View.GAMENOTICE);
        }
      }
      
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException ex) {
      ex.printStackTrace();
    }
  }

  public void write(String message) throws IOException{
    os.write(message);
    os.newLine();
    os.flush();
  }
  
  public String requestify(String command, int content_l, String params, String content) {
    return command + "#" + content_l + "#" + params + "#" + content;
  }

  public Socket getSocketOfClient() {
    return socketOfClient;
  }
}
