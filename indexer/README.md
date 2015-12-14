Indexer
==========

To run from repl

 sbt -Dbamboo.privatekey=[privatekey] -Dbamboo.privatepass=[privatepass] -Dstash.username=[stashusername] \  
  -Dstash.password=[stashpass] -DrepoFolder=[wheretoclone] -Des.url=[url of elasticsearch if different from localhost]

In repl to run full flow 
 
```scala
import Indexing._
indexer.runForeach(println(_)) onComplete { _ => println("Done indexing") }
```
