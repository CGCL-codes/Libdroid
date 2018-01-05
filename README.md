# Libdroid
Advancing on our previous work (contianer-based server runtime [Rattrap](https://github.com/CGCL-codes/Rattrap)), we implement an unikernel-based runtime for mobile computation offloading under Mobile Fog Computing or Mobile Edge Computing scenarios, Introducing much less boot-up delay, memory footprint, disk usage and energy consumption at IoT Edge. We firstly put forward the concept of Rich-Unikernel which aims to support various applications in one unikernel while avoiding their time-consuming recompilation. Following the design of Rich-Unikernel, we implement a not only lightweight but also flexible runtime for offloaded codes, called Android Unikernel, by integrating basic Android system libraries into [OSv unikernel](http://osv.io/).
![System Architechture](https://github.com/CGCL-codes/Libdroid/blob/master/figures/arch.png)

# 1. Project structure

1.1 Unikernel-Library
-------
  An offloading framework, which provides APIs for mobile applications to quickly offload local computation-intensive tasks to server side to execute. See examples in [Applications](https://github.com/CGCL-codes/Libdroid/tree/master/Applications)
  
1.2 Unikernel-Scheduler
-------
  A scheduling-programe——Dispatcher. It is in charge of handling offloading requests and booting up an unikernel server for each request.
  
1.3 Unikernel-Server
-------
  This part is to build into unikernel. It contains a tool called DynamicLinker which is able to load class from Android bytecodes(.dex) and link application into unikernel at runtime. When an unikernel boots up, this programme runs automatically and waits for offloading requests. With the help of DynamicLinker, we can omit a lot of time-consuming recompiling work when building application into its corresponding unikernel.
  
1.4 Libdroid
-------
  This part contains common Android system libraries that may be used by offloaded codes. It is also packaged into unikernel image along with DynamicLinker at compile-time.

# 2. Rich-Unikernel: concept and implementation
  Since traditional unikernel takes a few seconds to be specicalized for an application and needs to be reconstructed when application changes even if the change is very small, it is not suitable for offloading scenarios which often meet various applications and need to response to requests timely. Therefore, We define Rich-Unikernel, a kind of unikernel that is more general than conventional unikernel. Rich-Unikernel is not specialized for a particular application and it can be regarded as a base unikernel for a series of applications. All of the system libraries needed by these applications have already been packaged into the base unikernel, so it is able to run different applications (one application at a time).
  
  In our system, we implement a kind of Rich-Unikernel called Android Unikernel, by integrating common Android libraries that are needed by offloaded codes into OSv unikernel. Also, We impletement a tool called DynamicLinker, which is able to dynamically link application codes into Android Unikernel at runtime. Thus, it can serve various applications without time-consuming recompilation, greately improving the response speed of our system.
  
# 3. How to deploy
=====
