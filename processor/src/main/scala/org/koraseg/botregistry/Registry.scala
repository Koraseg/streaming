package org.koraseg.botregistry

trait Registry[K, V] {

  def put(key: K, value: V): Boolean

  def get(key: K): V
}
