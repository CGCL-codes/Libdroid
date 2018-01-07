# Step 1
Package the Libdroid into Libdroid.jar and Unikernel-Server into Unikernel-Server.jar
```
$ jar cvf Libdroid.jar Libdroid
$ jar cvf Unikernel-Server.jar Unikernel-Server
```
# Step 2
Create a configuration file [module.py]()
```
from osv.modules import api

api.require('java')

default = api.run('/java.so -jar /Unikernel-Server.jar')
```
