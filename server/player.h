
#ifndef _PLAYER_H_
#define _PLAYER_H_

#include <mysql/mysql.h>

#include "http.h"

typedef struct Achievement {
  int win;
  int draw;
  int loss;
  int streak;
  int points;
} Achievement;

typedef struct User {
  int id;
  int sock;
  int game;
  char avatar[AVATAR_L];
  char username[USERNAME_L];
  char password[PASSWORD_L];
  int friends[FRIEND_L];
  Achievement achievement;
  bool is_online;
  bool is_playing;
} Player;

#pragma once
typedef struct rbtree PlayerTree;

#include "game.h"

PlayerTree *player_build(MYSQL *);
void player_drop(PlayerTree *);
int player_add(PlayerTree *, Player);
Player *player_find(PlayerTree *, int);
int player_fd(PlayerTree *, int);
char *player_username(PlayerTree *, int);

int my_rank(MYSQL *, int, char *);
int rank(MYSQL *, ClientAddr, GameTree *, PlayerTree *, Message *, int *);
int profile(MYSQL *, ClientAddr, GameTree *, PlayerTree *, Message *, int *);

int friend_check(MYSQL *, ClientAddr, GameTree *, PlayerTree *, Message *, int *);
int friend_list(MYSQL *, ClientAddr, GameTree *, PlayerTree *, Message *, int *);
int friend_add(MYSQL *, ClientAddr, GameTree *, PlayerTree *, Message *, int *);
int friend_accept(MYSQL *, ClientAddr, GameTree *, PlayerTree *, Message *, int *);

#endif