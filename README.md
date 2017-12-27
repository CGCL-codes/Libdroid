# Libdroid
An unikernel-based runtime for mobile computation offloading under Mobile Fog Computing or Mobile Edge Computing scenarios.
![System Architechture](https://github.com/CGCL-codes/Libdroid/blob/master/figures/arch.png)

1 Project structure
=====
1.1 Unikernel-Library
-------
  An offloading framework, which provides APIs for mobile applications to quickly offload local computation-intensive tasks to server side to execute. See examples in directory Applications/
1.2 Unikernel-Scheduler
-------
  A scheduling-programe——Dispatcher. It is in charge of handling offloading requests and booting up an unikernel server for each request. 
1.3 Unikernel-Server
-------
  This part is to build into unikernel. It contains a tool called DynamicLinker which is able to load class from Android bytecodes(.dex) and link application into unikernel at runtime. When an unikernel boots up, this programme runs automatically and waits for offloading requests. With the help of DynamicLinker, we can omit a lot of time-consuming recompiling work when building application into its corresponding unikernel.
1.4 Libdroid
-------
  This part contains common Android system libraries that may be used by offloaded codes.
