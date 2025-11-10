# BattleCell App Documentation

This App wants to be an enjoyable Game which is oriented in some sort after browsergames.
There a player has its own attributes he can increase by his own training and testing his game-character against other players.


# What it does:
The App asks on first execution how you want to name your character.
Then your shown the Main Menu in which they player has 3 Options to choose from Search for players, Training and Your character.

“Search for players”: The player sees a list which of android devices to attack.(not functional)
There is a button to fight against a NPC.
There he is matched against a random generated Character.
The matching consists of comparing the power attribute of both characters, the one with the higher value wins.
In the special case of equality, a game of Russian roulette is started.

“Training”: Here you can choose between 2 games of chasing a bug.
Game1:
Starts a game where you have to touch a running picture on its way to the center of the screen.
It walks a set path but has a randomized start position. If you touch it, you win. 
If it reaches the center you lose. The difficulty lays in the speed and the visibility of the picture. 
When won your power attribute is increased.
Game2:
This game basicaly is just a version with higher dificultie since the bug is seldom visible. 
But there is a higher reward for winning since the amount of power the character gains is directly correlated with the time needed to catch  the bug. 
 
“Your character” displays all data from your character.


![State diagram](https://raw.githubusercontent.com/wannerdev/BattleCell/master/state%20diagram.png)











# How it is structured:
There is a Character class which mainly saves attributes except for the fight() method.
Furthermore there is a SQLitedatabase Class Data which implements the singleton pattern to secure access.
The database stores all Character contents. 
In the MainActivity we have an Aggregation with the Data class.
Each Layout xml file has its own Acivity Class. The views are mostly xml files only the games are SurfaceViews.
They have their own dynamically painted Canvas. Each Canvas has its own thread which draws as long as the game has not ended.
The WLAn P2P stuff is already in the project the example is implemented but only partial working.


# What it’s supposed to become:
At this time the database doesn’t really make sense but it was designed to store every Character the player meets. this would enable the player to fight them even when they are not around.

“Training” is supposed to be a menu for little training games, where each game trains a different attribute of the character.
The second game is the flickering Bug in the future there should be more routes which get selected randomly or maybe a path finding algorithm with a map. 
The search is supposed to show all other W-LAN android cellphones, bluetooth is also an option.
There you could choose whose cellphone to attack. If there are none you can choose to fight against NPCs. 
If there are some they would get a randomized enemy based on the smartphone, or based on mac adresses of the devices around. 
In the future the idea would be to comunicate with the smartphones in the same network asking them if they have the app and if yes get their characters attributes.


