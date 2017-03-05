# carrot: 

##

Library to keep your rabbits running...

![alt tag](https://cloud.githubusercontent.com/assets/3204818/23513284/5d24a108-ff5b-11e6-8f0d-12126f820385.png) 

A Clojure library designed to provide you with the implementation of the following simple RabbitMq delayed retry mechanism:
![alt tag](https://cloud.githubusercontent.com/assets/3204818/23512162/99eec068-ff57-11e6-9176-a883f79a9e22.png)


The idea is the following:

1. Provider sends a message to the message queue
2. Consumer tries to process it
3. While processing some exception is thrown
4. Failed message gets into the waiting-queue for a period of time which can be configured (ttl)
5. After the ttl expired the message is put back to message queue to try to process it again
6. Steps 3-5 repeated until the message successfully processed or until the number of retries are less than the max-retry you running carrot with.
7. When we exceed max retry, we put the message in the corresponding dead-letter queue sorted.

## Releases and Dependency Information

* I publish releases to [Clojars]

* Latest snapshot release is [carrot "0.1.1-SNAPSHOT"]

* [All releases]

[Leiningen] dependency information:

   [carrot "0.1.1-SNAPSHOT"]

## Usage

No need to worry if the above diagram seems to be too complicated. The idea is that you give custom names to the queues and exchanges you find on the diagram and Carrot will provide you with the retry mechanism and will create the architecturev for you.
Example:
[./src/carrot/examples/](example.clj)

## License

Copyright © 2017 Gabor Raski

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
