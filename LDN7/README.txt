MINIMALNE ZAHTEVE
	JDK 8 ali novejši  
	Knjižnica json-simple 1.1.1 (v mapi "lib/")

ZAGANJANJE WINDOWS
  javac -d bin -cp "lib\json-simple-1.1.1.jar" src\ChatServer.java src\ChatClient.java
  java  -cp "bin;lib\json-simple-1.1.1.jar"   ChatServer
  java  -cp "bin;lib\json-simple-1.1.1.jar"   ChatClient

ZAGANJANJE LINUX
  javac -d bin -cp "lib/json-simple-1.1.1.jar" src/ChatServer.java src/ChatClient.java
  java  -cp "bin:lib/json-simple-1.1.1.jar"   ChatServer
  java  -cp "bin:lib/json-simple-1.1.1.jar"   ChatClient

KOMUNIKACIJA:
	/public [vsebina] - pošlje vsebino vsem povezanim klientom
	/private [user] [vsebina] - pošlje vsebino uporabniku *client*
	/listClients - izpis vseh trenutno povezanih uporabnikov
	/leave - zapustitev klepeta

DODATNE FUNKCIONALNOSTI:
	Error message: kadar uporabnik želi kontaktirati drugega, ampak tega ni online, server vrne error sporočilo
	Alert: ob prijavi se vsem uporabnikom razpošlje opozorilo o novem uporabniku v klepetu, enako velja za odklop od pogovora
	Dupliciranje up. imen: če se poskuša nov uporabnik prijaviti pod imenom, ki je že zasedeno, mu server vrne napako in ga zavrne

STRUKTURA JSON SPOROCILA

	type - tip sporocila
	sender - ime pošiljatelja
	targetUser - ime naslovnika
	content - vsebina
	cas - čas (za izpis časa se opravičujem, je grd in povsod sem brskal za naš timezone, zato je tudi implementacija v kodi grda)

PS: komentarje sem pustil kakeršni so bili v source kodi, če sem katerega koli dodal ne bi smel biti kritičen. Vse o delovanju je vključeno v tejle README datoteki

--Samuel Logar, vpisna 63240194