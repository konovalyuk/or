package com.mavan.or.newpost.example;
import com.mavan.or.newpost.model.*;
import com.mavan.or.newpost.optimization.NetworkOptimizer;
public class NewPostDirectTest { public static void main(String[] a){ NewPostProblem p=NewPostProblem.builder()
.locations(java.util.List.of("A","B","C"))
.demand(new double[][]{{0,9,0},{0,0,9},{0,0,0}})
.distance(new double[][]{{0,149,100},{152,0,225},{185,223,0}})
.terminalCapacity(new double[]{25,85,90})
.terminalHandlingCost(new double[]{28,57,57})
.vehicleTypes(java.util.List.of(new VehicleType("20",20,15.64,0),new VehicleType("10",10,10.8,0),new VehicleType("5",5,5.23,0)))
.directOnly(true).build();
var r=new NetworkOptimizer().solve(p); System.out.println(r.getStatus()+" "+r.getTotalCost()+" nodes="+r.getSearchNodes());}}
