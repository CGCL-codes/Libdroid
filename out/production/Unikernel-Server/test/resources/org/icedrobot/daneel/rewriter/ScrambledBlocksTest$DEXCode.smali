##
# Daneel - Dalvik to Java bytecode compiler
# Copyright (C) 2011  IcedRobot team
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# This file is subject to the "Classpath" exception:
#
# Linking this library statically or dynamically with other modules is
# making a combined work based on this library.  Thus, the terms and
# conditions of the GNU General Public License cover the whole
# combination.
#
# As a special exception, the copyright holders of this library give you
# permission to link this library with independent modules to produce an
# executable, regardless of the license terms of these independent
# modules, and to copy and distribute the resulting executable under terms
# of your choice, provided that you also meet, for each linked independent
# module, the terms and conditions of the license of that module.  An
# independent module is a module which is not derived from or based on
# this library.  If you modify this library, you may extend this exception
# to your version of the library, but you are not obligated to do so.  If
# you do not wish to do so, delete this exception statement from your
# version.
##

.class public Lorg/icedrobot/daneel/rewriter/ScrambledBlocksTest$DEXCode;
.implements Lorg/icedrobot/daneel/rewriter/ScrambledBlocksTest$DEXInterface;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public check()F
    .registers 2
    goto :L2
  :L1
    move v1, v0
    return v1
  :L2
    const v0, 0.5f
    goto :L1
.end method
