# BattleCell App Documentation
# What it does:
The App asks on first execution how you want to name your character. Then your shown the Main Menu in which they player has 3 Options to choose from Search for players, Training and Your character.

“Search for players”: The player sees a list which of android devices to attack.(not functional)
There is a button to fight against a NPC. There he is matched against a random generated Character. The matching consists of comparing the power attribute of both characters the one with the higher value wins. In the special case of equality, a game of Russian roulette is started. 
“Training”: Here you can choose between 2 games of chasing a bug.
Starts a game where you have to touch a running picture on its way to the center of the screen. If you touch it, you win. If it reaches the center you lose. The difficulty lays in the speed and the visibility of the picture. When won your power attribute is increased.

“Your character” displays all data from your character.


![alt text](https://raw.githubusercontent.com/wannerdev/BattleCell/master/state%20diagram.png)











# How it is organized:
There is a Character class which mainly saves attributes except for the fight() method.
Furthermore there is a SQLitedatabase Class Data which implements the singleton pattern to secure access. Of course it stores all Character content. 
In the MainActivity we have an Aggregation with the Data class.
Each Layout xml file is has its own Acivity Class. The views are mostly xml files only the games are SurfaceViews. They have their own dynamically painted Canvas. And each their own Thread which draws as long as the game has not ended.
The WLAn P2P stuff is already in the project the example is implemented but only partial working.
What it’s supposed to become:
At this time the database doesn’t really make sense but it was designed to store every Character you met so could fight them even when they are not around.
“Training” is supposed to be a menu for little training games, which each train different attributes of the character. The first game is the not flickering bug, which is supposed to aim in the direction where the bug is heading.
The second game is the flickering Bug in the future there should be more routes which get selected randomly or maybe a path finding algorithm with a map. 
The search is supposed to show all other W-LAN android cellphones.
There you could choose whose cellphone to attack. If there are none you can choose to fight against NPCs. 


