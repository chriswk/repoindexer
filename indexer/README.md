Indexer
==========

# Elasticsearch
- Elasticsearch needs more than 1 GB heap space to succeed in indexing FINN repo
	- Use ES_HEAP_SIZE=2g or more


# To run from repl

 sbt -Dbamboo.privatekey=[privatekey] -Dbamboo.privatepass=[privatepass] -Dstash.username=[stashusername] \  
  -Dstash.password=[stashpass] -DrepoFolder=[wheretoclone] -Des.url=[url of elasticsearch if different from localhost]

In repl to run full flow 
 
```scala
import Indexing._
indexer.runForeach(println(_)) onComplete { _ => println("Done indexing") }
```
