#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "http.h"
#include "player.h"
#include "game.h"
#include "utils.h"
#include "rbtree.h"

static int game_cmp(const void *p1, const void *p2) {
  Game *game1, *game2;

  game1 = (Game *)p1;
  game2 = (Game *)p2;

  if(game1->id > game2->id) return 1;
  else if(game1->id < game2->id) return -1;
  else return 0;
}

static void *game_dup(void *p) {
  void *dup_p;

  dup_p = calloc(1, sizeof(Game));
  memmove(dup_p, p, sizeof(Game));

  return dup_p;
}

static void game_rel(void *p) { free(p); }

GameTree *game_new() {
  rbtree_t *rbtree;
  rbtree = rbnew(game_cmp, game_dup, game_rel);
  logger(L_SUCCESS, "Build game tree successfully...");
  return rbtree;
}

void game_drop(GameTree *gametree) { rbdelete(gametree); }

int game_add(GameTree *gametree, Game new_game) {
  int ret;

  Game *game;
  game = calloc(1, sizeof(Game));
  game->id = new_game.id;
  game->result = new_game.result;
  game->player1_id = new_game.player1_id;
  game->player2_id = new_game.player2_id;
  game->num_move = new_game.num_move;
  strcpy(game->password, new_game.password);

  ret = rbinsert(gametree, (void *)game);
  if (ret == 0) {
    free(game);
    return -1;
  }

  return 0;
}

int game_delete(GameTree *gametree, int id) {
  int ret;
  Game *game;

  game = calloc(1, sizeof(Game));
  game->id = id;

  ret = rberase(gametree, (void *)game);
  if (ret == 0) {
    free(game);
    return -1;
  }

  return 0;
}

Game *game_find(GameTree *gametree, int id) {
  Game *game, game_find;

  game_find.id = id;
  game = rbfind(gametree, &game_find);

  return !game ? NULL : game;
}

int game_finish(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int game_id = atoi(map_val(msg->params, "game_id"));
  int player_id = atoi(map_val(msg->params, "player_id"));
  int opponent_id = atoi(map_val(msg->params, "opponent_id"));
  int x = atoi(map_val(msg->params, "x"));
  int y = atoi(map_val(msg->params, "y"));
  int result = atoi(map_val(msg->params, "result"));
  char type[10];
  strcpy(type, map_val(msg->params, "type"));

  // TODO: Find game -> Update game board
  Game *game = game_find(gametree, game_id);
  char dataStr[DATA_L];
  memset(dataStr, '\0', sizeof dataStr);

  Player *player = player_find(playertree, player_id);
  Player *opponent = player_find(playertree, opponent_id);
  char query[QUERY_L];
  memset(query, '\0', QUERY_L);

  switch (result) {
    case 1: // Win
      // TODO: Update gametree and playertree
      game->result = player_id;
      ++player->game;
      ++player->achievement.win;
      player->achievement.points += 3;

      ++opponent->game;
      ++opponent->achievement.loss;

      // TODO: Update database
      sprintf(
        query,
        "UPDATE players SET game = %d, win = %d, points = %d WHERE id = %d",
        player->game, player->achievement.win, player->achievement.points, player_id
      );
      mysql_query(conn, query);

      sprintf(
        query,
        "UPDATE players SET game = %d, loss = %d WHERE id = %d",
        opponent->game, opponent->achievement.loss, opponent_id
      );
      mysql_query(conn, query);

      sprintf(
        query,
        "INSERT INTO histories (player1_id, player2_id, result, num_moves) VALUES (%d, %d, 1, %d)",
        player_id, opponent_id, game->num_move);
      mysql_query(conn, query);

      if(strcmp(type, "caro") == 0) {
        receiver[0] = opponent->sock;
        sprintf(dataStr, "x=%d,y=%d", x, y);
        responsify(msg, "caro", dataStr);
      }
      else if(strcmp(type, "timeout") == 0) {
        game->num_move = 0;
        receiver[0] = player->sock;
        receiver[1] = opponent->sock;
        responsify(msg, "new_game", NULL);
      }
      break;

    case -1: // LOSS
      if(strcmp(type, "timeout") == 0) {
        receiver[0] = opponent->sock;
        memset(dataStr, '\0', DATA_L);
        sprintf(dataStr, "game_id=%d,opponent_id=%d", game_id, player_id);
        responsify(msg, "timeout", dataStr);
      }
      else if(strcmp(type, "caro") == 0) {
        game->num_move = 0;
        receiver[0] = player->sock;
        receiver[1] = opponent->sock;
        responsify(msg, "new_game", NULL);
      }
      break;
  }

  return SUCCESS;
}

int caro(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int game_id = atoi(map_val(msg->params, "game_id"));
  int opponent_id = atoi(map_val(msg->params, "opponent_id"));
  int x = atoi(map_val(msg->params, "x"));
  int y = atoi(map_val(msg->params, "y"));

  // TODO: Find game -> Update game board
  Game *game = game_find(gametree, game_id);
  game->num_move++;
  char dataStr[DATA_L];
  memset(dataStr, '\0', sizeof dataStr);

  receiver[0] = player_fd(playertree, opponent_id);
  sprintf(dataStr, "x=%d,y=%d", x, y);
  responsify(msg, "caro", dataStr);
  return SUCCESS;
}

int game_create(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int player_id = atoi(map_val(msg->params, "player_id"));
  char game_pwd[PASSWORD_L];
  strcpy(game_pwd, !map_val(msg->params, "password") ? "" : map_val(msg->params, "password"));

  rbtrav_t *rbtrav;
  rbtrav = rbtnew();
  Game *last_game = rbtlast(rbtrav, gametree);

  // TODO: Create game
  Game new_game = {
    .id = !last_game ? 1 : last_game->id + 1,
    .num_move = 0,
    .result = -1,
    .player1_id = player_id,
    .player2_id = 0,
  };

  if(strlen(game_pwd) > 0) strcpy(new_game.password, game_pwd);
  else memset(new_game.password, '\0', PASSWORD_L);

  game_add(gametree, new_game);

  char dataStr[DATA_L];
  memset(dataStr, '\0', sizeof dataStr);
  if(strlen(game_pwd) > 0) sprintf(dataStr, "game_id=%d,password=%s", new_game.id, game_pwd);
  else sprintf(dataStr, "game_id=%d", new_game.id);
  responsify(msg, "game_created", dataStr);
  return SUCCESS;
}

int game_cancel(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int game_id = atoi(map_val(msg->params, "game_id"));
  game_delete(gametree, game_id);
  responsify(msg, "game_cancel", NULL);
  return SUCCESS;
}

int game_join(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int player_id = atoi(map_val(msg->params, "player_id"));
  int game_id = atoi(map_val(msg->params, "game_id"));
  char dataStr[DATA_L], game_pwd[PASSWORD_L];
  memset(dataStr, '\0', DATA_L);
  char *temp_pwd = map_val(msg->params, "password");
  strcpy(game_pwd, temp_pwd ? temp_pwd : "");

  // TODO: Find game room for player
  Game *game_found = game_find(gametree, game_id);

  if(!game_found) {
    responsify(msg, "game_null", NULL);
    return FAILURE;
  }

  // TODO: Check room state
  if(strcmp(game_found->password, game_pwd) != 0) {
    responsify(msg, "game_password_incorrect", NULL);
    return FAILURE;
  }

  if(game_found->player1_id && game_found->player2_id) {
    responsify(msg, "game_full", NULL);
    return FAILURE;
  }

  if(game_found->player1_id) game_found->player2_id = player_id;
  else game_found->player1_id = player_id;

  Player *player_found = player_find(playertree, player_id);
  player_found->is_playing = true;

  Player *opponent = player_find(playertree, (game_found->player1_id == player_id ? game_found->player2_id : game_found->player1_id));
  opponent->is_playing = true;
  char tmp[DATA_L];
  memset(tmp, '\0', DATA_L);

  sprintf(
    dataStr,
    "game_id=%d,is_start=1,ip=127.0.0.1,"
    "id=%d,username=%s,avatar=%s,game=%d,win=%d,draw=%d,loss=%d,points=%d,rank=%d;"
    "game_id=%d,is_start=0,ip=127.0.0.1,"
    "id=%d,username=%s,avatar=%s,game=%d,win=%d,draw=%d,loss=%d,points=%d,rank=%d",
    game_found->id, opponent->id, opponent->username, opponent->avatar, opponent->game,
    opponent->achievement.win, opponent->achievement.draw, opponent->achievement.loss, opponent->achievement.points,
    my_rank(conn, opponent->id, tmp),
    game_found->id, player_found->id, player_found->username, player_found->avatar, player_found->game,
    player_found->achievement.win, player_found->achievement.draw, player_found->achievement.loss, player_found->achievement.points,
    my_rank(conn, player_found->id, tmp)
  );

  receiver[1] = opponent->sock;
  responsify(msg, "game_joined", dataStr);
  return SUCCESS;
}

int game_quit(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int player_id = atoi(map_val(msg->params, "player_id"));
  int game_id = atoi(map_val(msg->params, "game_id"));
  int opponent_id = atoi(map_val(msg->params, "opponent_id"));

  // TODO: Find game room for player
  Game *game = game_find(gametree, game_id);

  Player *quit_player = player_find(playertree, player_id);
  Player *winner = player_find(playertree, opponent_id);
  quit_player->is_playing = false;
  winner->is_playing = false;

  // TODO: Handle first player quit room
  if(game->player1_id && game->player2_id) {
    char query[QUERY_L];
    memset(query, '\0', QUERY_L);
    game->result = winner->id;
    ++quit_player->game;
    ++quit_player->achievement.loss;

    ++winner->game;
    ++winner->achievement.win;
    winner->achievement.points += 3;

    sprintf(
      query,
      "UPDATE players SET game = %d, win = %d, points = %d WHERE id = %d",
      winner->game, winner->achievement.win, winner->achievement.points, opponent_id
    );
    mysql_query(conn, query);

    sprintf(
      query,
      "UPDATE players SET game = %d, loss = %d WHERE id = %d",
      quit_player->game, quit_player->achievement.loss, player_id
    );
    mysql_query(conn, query);

    sprintf(
      query,
      "INSERT INTO histories (player1_id, player2_id, result, num_moves) VALUES (%d, %d, 1, %d)",
      winner->id, quit_player->id, game->num_move
    );
    mysql_query(conn, query);
  }

  game_delete(gametree, game_id);
  receiver[0] = winner->sock;
  responsify(msg, "game_quit", NULL);
  return SUCCESS;
}

int game_list(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  char dataStr[DATA_L];
  memset(dataStr, '\0', DATA_L);

  Game *game;

  rbtrav_t *rbtrav;
  rbtrav = rbtnew();
  game = rbtfirst(rbtrav, gametree);
  char line[1000];
  memset(line, '\0', 1000);

  do {
    sprintf(
      line,
      "game_id=%d,password=%s,num_move=%d,player1_id=%d,player2_id=%d;",
      game->id, game->password, game->num_move,
      game->player1_id, game->player2_id
    );
    strcat(dataStr, line);
  } while ((game = rbtnext(rbtrav)) != NULL);

  // TODO: Remove ; at last data
  dataStr[strlen(dataStr) - 1] = '\0';

  responsify(msg, "game_list", dataStr);
  return SUCCESS;
}

int game_quick(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int player_id = atoi(map_val(msg->params, "player_id"));
  char dataStr[DATA_L];
  memset(dataStr, '\0', DATA_L);

  // TODO: Find game no password for player
  Game *game;
  rbtrav_t *rbtrav;
  rbtrav = rbtnew();
  game = rbtfirst(rbtrav, gametree);

  do {
    if(strlen(game->password) > 0) continue;  // game has password
    else if(game->player1_id && game->player2_id) continue; // game full
    else {
      // FIND game !
      if(game->player1_id) game->player2_id = player_id;
      else game->player1_id = player_id;

      Player *player_found = player_find(playertree, player_id);
      player_found->is_playing = true;

      Player *opponent = player_find(playertree, (game->player1_id == player_id ? game->player2_id : game->player1_id));
      opponent->is_playing = true;
      char tmp[DATA_L];
      memset(tmp, '\0', DATA_L);

      sprintf(
        dataStr,
        "game_id=%d,is_start=1,ip=127.0.0.1,"
        "id=%d,username=%s,avatar=%s,game=%d,win=%d,draw=%d,loss=%d,points=%d,rank=%d;"
        "game_id=%d,is_start=0,ip=127.0.0.1,"
        "id=%d,username=%s,avatar=%s,game=%d,win=%d,draw=%d,loss=%d,points=%d,rank=%d",
        game->id, opponent->id, opponent->username, opponent->avatar, opponent->game,
        opponent->achievement.win, opponent->achievement.draw, opponent->achievement.loss, opponent->achievement.points,
        my_rank(conn, opponent->id, tmp),
        game->id, player_found->id, player_found->username, player_found->avatar, player_found->game,
        player_found->achievement.win, player_found->achievement.draw, player_found->achievement.loss, player_found->achievement.points,
        my_rank(conn, player_found->id, tmp)
      );

      receiver[1] = opponent->sock;
      responsify(msg, "game_joined", dataStr);
      return SUCCESS;
    }
  } while ((game = rbtnext(rbtrav)) != NULL);

  responsify(msg, NULL, NULL);
  return FAILURE;
}

int duel_request(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int player_id = atoi(map_val(msg->params, "player_id"));
  int friend_id = atoi(map_val(msg->params, "friend_id"));
  char dataStr[DATA_L];
  memset(dataStr, '\0', DATA_L);

  Player *requester = player_find(playertree, player_id);
  Player *friend = player_find(playertree, friend_id);
  sprintf(dataStr, "player_id=%d,username=%s", player_id, requester->username);
  receiver[0] = friend->sock;
  responsify(msg, "duel_request", dataStr);
  return SUCCESS;
}

int duel_handler(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int player_id = atoi(map_val(msg->params, "player_id"));
  int friend_id = atoi(map_val(msg->params, "friend_id"));
  int agree = atoi(map_val(msg->params, "agree"));
  char dataStr[DATA_L];
  memset(dataStr, '\0', DATA_L);

  Player *me = player_find(playertree, player_id);
  Player *friend = player_find(playertree, friend_id);

  // TODO: If disagree
  if(!agree) {
    receiver[0] = friend->sock;
    responsify(msg, "duel_rejected", NULL);
    return SUCCESS;
  }

  rbtrav_t *rbtrav;
  rbtrav = rbtnew();
  Game *last_game = rbtlast(rbtrav, gametree);

  // TODO: Create game
  Game new_game = {
    .id = !last_game ? 1 : last_game->id + 1,
    .num_move = 0,
    .result = -1,
    .player1_id = player_id,
    .player2_id = friend_id,
  };
  memset(new_game.password, '\0', PASSWORD_L);

  game_add(gametree, new_game);

  me->is_playing = friend->is_playing = true;

  char tmp[DATA_L];
  memset(tmp, '\0', DATA_L);

  sprintf(
    dataStr,
    "game_id=%d,is_start=0,ip=127.0.0.1,"
    "id=%d,username=%s,avatar=%s,game=%d,win=%d,draw=%d,loss=%d,points=%d,rank=%d;"
    "game_id=%d,is_start=1,ip=127.0.0.1,"
    "id=%d,username=%s,avatar=%s,game=%d,win=%d,draw=%d,loss=%d,points=%d,rank=%d",
    new_game.id, me->id, me->username, me->avatar, me->game,
    me->achievement.win, me->achievement.draw, me->achievement.loss, me->achievement.points,
    my_rank(conn, me->id, tmp),
    new_game.id, friend->id, friend->username, friend->avatar, friend->game,
    friend->achievement.win, friend->achievement.draw, friend->achievement.loss, friend->achievement.points,
    my_rank(conn, friend->id, tmp)
  );

  receiver[1] = friend->sock;
  responsify(msg, "duel_accepted", dataStr);
  return SUCCESS;
}

int draw_request(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int opponent_id = atoi(map_val(msg->params, "opponent_id"));
  receiver[0] = player_fd(playertree, opponent_id);
  responsify(msg, "draw_request", NULL);
  return SUCCESS;
}

int draw_handler(MYSQL *conn, ClientAddr clnt_addr, GameTree *gametree, PlayerTree *playertree, Message *msg, int *receiver) {
  int game_id = atoi(map_val(msg->params, "game_id"));
  int player_id = atoi(map_val(msg->params, "player_id"));
  int opponent_id = atoi(map_val(msg->params, "opponent_id"));
  int confirm = atoi(map_val(msg->params, "confirm"));
  char dataStr[DATA_L];
  memset(dataStr, '\0', DATA_L);

  Player *me = player_find(playertree, player_id);
  Player *opponent = player_find(playertree, opponent_id);
  Game *game = game_find(gametree, game_id);

  if(confirm) {
    char query[QUERY_L];
    memset(query, '\0', sizeof(query));
    game->result = 0;

    ++me->game;
    ++me->achievement.draw;
    ++me->achievement.points;

    ++opponent->game;
    ++opponent->achievement.draw;
    ++opponent->achievement.points;

    sprintf(
      query,
      "UPDATE players SET game = %d, draw = %d, points = %d WHERE id = %d",
      me->game, me->achievement.draw, me->achievement.points, player_id
    );
    mysql_query(conn, query);

    sprintf(
      query,
      "UPDATE players SET game = %d, draw = %d, points = %d WHERE id = %d",
      opponent->game, opponent->achievement.draw, opponent->achievement.points, opponent_id
    );
    mysql_query(conn, query);

    sprintf(query, "INSERT INTO histories (player1_id, player2_id, result, num_moves) VALUES (%d, %d, 0, %d)", player_id, opponent_id, game->num_move);
    mysql_query(conn, query);
    game->num_move = 0;
    receiver[0] = me->sock;
    receiver[1] = opponent->sock;
    responsify(msg, "draw", NULL);
  }
  else {
    responsify(msg, "draw_refuse", NULL);
    receiver[0] = opponent->sock;
  }

  return SUCCESS;
}
