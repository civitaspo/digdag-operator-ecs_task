package pro.civitaspo.digdag.plugin.ecs_task.util

import io.digdag.client.config.{Config, ConfigFactory}

import scala.collection.JavaConverters._
import scala.reflect._

case class DigdagConfig(config: Config) extends Config(config) {
  def get[E: ClassTag](key: String): E = get(key, classTag[E].runtimeClass.asInstanceOf[Class[E]])
  def getOpt[E: ClassTag](key: String): Option[E] = Option(getOptional(key, classTag[E].runtimeClass.asInstanceOf[Class[E]])).map(_.orNull())

  def getSeq[E: ClassTag](key: String): Seq[E] = getList[E](key, classTag[E].runtimeClass.asInstanceOf[Class[E]]).asScala

  def getSeqOpt[E: ClassTag](key: String): Option[Seq[E]] = {
    val seq = getListOrEmpty(key, classTag[E].runtimeClass.asInstanceOf[Class[E]]).asScala
    if (seq.nonEmpty) Some(seq) else None
  }
  def parseSeq[E: ClassTag](key: String): Seq[E] = parseList(key, classTag[E].runtimeClass.asInstanceOf[Class[E]]).asScala

  def parseSeqOpt[E: ClassTag](key: String): Option[Seq[E]] = {
    val seq = parseListOrGetEmpty(key, classTag[E].runtimeClass.asInstanceOf[Class[E]]).asScala
    if (seq.nonEmpty) Some(seq) else None
  }

  def parseNestedOpt(key: String): Option[DigdagConfig] = {
    val c = parseNestedOrGetEmpty(key)
    if (c.isEmpty) None else Some(DigdagConfig(c))
  }

  def parseNestedOptSeq(key: String): Option[Seq[DigdagConfig]] = {
    val c = parseListOrGetEmpty(key, classOf[DigdagConfig]).asScala
    if (c.isEmpty) None else Some(c)
  }

  def getMap[E: ClassTag, V: ClassTag](key: String): Map[E, V] =
    getMap(key, classTag[E].runtimeClass.asInstanceOf[Class[E]], classTag[V].runtimeClass.asInstanceOf[Class[V]]).asScala.toMap

  def getMapOpt[E: ClassTag, V: ClassTag](key: String): Option[Map[E, V]] = {
    val map = getMapOrEmpty(key, classTag[E].runtimeClass.asInstanceOf[Class[E]], classTag[V].runtimeClass.asInstanceOf[Class[V]]).asScala.toMap
    if (map.nonEmpty) Some(map) else None
  }
  override def getNested(key: String): DigdagConfig = DigdagConfig(this.config.getNested(key))

  def getNestedOpt(key: String): Option[DigdagConfig] = {
    val c = this.config.getNestedOrGetEmpty(key)
    if (c.isEmpty) None else Some(DigdagConfig(c))
  }

  override def set(key: String, v: Any): DigdagConfig = DigdagConfig(super.set(key, v))
  def setNested(key: String, v: DigdagConfig): DigdagConfig = DigdagConfig(super.setNested(key, v))

}
