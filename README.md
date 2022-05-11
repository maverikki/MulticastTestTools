# MulticastTestTools

``Start receiver:``

    > javac .\MulticastReceiver.java
    > java MulticastReceiver
    Parameters required: address, port, ownid
    > java MulticastReceiver 239.1.2.3 12345 1



``Start sender:``

    > javac .\MulticastSender.java
    > java MulticastSender
    Required parameters: ID, address, port, amount, delayMs, length, ttl
    > java MulticastSender 2 239.1.2.3 12345 0 1000 50 3
