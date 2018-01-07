from osv.modules import api

api.require('java')

default = api.run('/java.so -jar /Unikernel-Server.jar')
