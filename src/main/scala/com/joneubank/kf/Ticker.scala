package com.joneubank.kf

class Ticker {
  private var _start = System.currentTimeMillis
  private var _interval = _start.longValue

  val seconds: Boolean = false

  def start(): Long = {
    _start = System.currentTimeMillis
    _start
  }

  def total(): Long = {
    val total = System.currentTimeMillis - _start
    total
  }

  def interval(): Long = {
    val time = System.currentTimeMillis
    val interval = time - _interval
    _interval = time

    interval
  }
}
