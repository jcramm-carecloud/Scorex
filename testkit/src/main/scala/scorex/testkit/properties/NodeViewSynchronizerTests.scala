package scorex.testkit.properties

import akka.actor._
import akka.testkit.TestProbe
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import scorex.core.NodeViewHolder
import scorex.core.network.{Broadcast, ConnectedPeer, NetworkController, NodeViewSynchronizer}
import scorex.core.consensus.{History, SyncInfo}
import scorex.core.network.message.Message.MessageCode
import scorex.core.transaction.box.proposition.Proposition
import scorex.core.transaction.state.MinimalState
import scorex.core.transaction.wallet.Vault
import scorex.core.transaction.{MemoryPool, Transaction}
import scorex.core.utils.ScorexLogging
import scorex.core.{ModifierId, ModifierTypeId, PersistentNodeViewModifier}
import scorex.testkit.generators.{SyntacticallyTargetedModifierProducer, TotallyValidModifierProducer}
import scorex.testkit.utils.{FileUtils, SequentialAkkaFixture}
import scorex.core.network.message._
import scorex.core.serialization.{BytesSerializable, Serializer}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Try}

// todo: think about the following:
// with the current testing architecture, when a Scorex user (e.g. in "examples") wants to test his/her blockchain,
// he/she writes a NodeViewSynchronizerSpec extending NodeViewSynchronizerTests, and this will execute some tests
// that are actually independent of the particularities of his/her blockchain. Maybe we should test such
// blockchain-non-specific properties in scorex's core, instead of testkit.

// todo: remove unnecessary type parameters and traits
trait NodeViewSynchronizerTests[P <: Proposition,
TX <: Transaction[P],
PM <: PersistentNodeViewModifier,
ST <: MinimalState[PM, ST],
SI <: SyncInfo,
HT <: History[PM, SI, HT],
MPool <: MemoryPool[TX, MPool],
VL <: Vault[P, TX, PM, VL]]
  extends SequentialAkkaFixture
    with Matchers
    with PropertyChecks
    with ScorexLogging
    with SyntacticallyTargetedModifierProducer[PM, SI, HT]
    with TotallyValidModifierProducer[PM, ST, SI, HT] {

  type Fixture = SynchronizerFixture

  def nodeViewSynchronizer(implicit system: ActorSystem): (ActorRef, PM, TX, ConnectedPeer, TestProbe, TestProbe, TestProbe, TestProbe)

  class SynchronizerFixture extends AkkaFixture with FileUtils {
    val (node, mod, tx, peer, pchProbe, ncProbe, vhProbe, liProbe) = nodeViewSynchronizer
  }

  def createAkkaFixture(): Fixture = new SynchronizerFixture

  import NodeViewHolder._   // NodeViewHolder's messages
  import NodeViewSynchronizer._   // NodeViewSynchronizer's messages
  import NetworkController._      // NetworkController's messages

  property("NodeViewSynchronizer: SuccessfulTransaction") { ctx =>
    import ctx._
    node ! SuccessfulTransaction[P, TX](tx)
    ncProbe.fishForMessage(3 seconds) { case m => m.isInstanceOf[SendToNetwork] }
  }

  property("NodeViewSynchronizer: FailedTransaction") { ctx =>
    import ctx._
    node ! FailedTransaction[P, TX](tx, new Exception)
    // todo: NVS currently does nothing in this case. Should check banning.
  }

  property("NodeViewSynchronizer: SyntacticallySuccessfulModifier") { ctx =>
    import ctx._
    node ! SyntacticallySuccessfulModifier(mod)
    // todo ? : NVS currently does nothing in this case. Should it do?
  }

  property("NodeViewSynchronizer: SyntacticallyFailedModification") { ctx =>
    import ctx._
    node ! SyntacticallyFailedModification(mod, new Exception)
    // todo: NVS currently does nothing in this case. Should check banning.
  }

  property("NodeViewSynchronizer: SemanticallySuccessfulModifier") { ctx =>
    import ctx._
    node ! SemanticallySuccessfulModifier(mod)
    ncProbe.fishForMessage(3 seconds) { case m => m.isInstanceOf[SendToNetwork] }
  }

  property("NodeViewSynchronizer: SemanticallyFailedModification") { ctx =>
    import ctx._
    node ! SemanticallyFailedModification(mod, new Exception)
    // todo: NVS currently does nothing in this case. Should check banning.
  }

  property("NodeViewSynchronizer: GetLocalSyncInfo") { ctx =>
    import ctx._

  }

  property("NodeViewSynchronizer: CurrentSyncInfo") { ctx =>
    import ctx._

  }

  property("NodeViewSynchronizer: DataFromPeer: SyncInfoSpec") { ctx =>
    import ctx._

    val dummySyncInfoMessageSpec = new SyncInfoMessageSpec[SyncInfo](_ => Failure[SyncInfo](???)) { }

    val dummySyncInfo = new SyncInfo {
      def answer: Boolean = true
      def startingPoints: History.ModifierIds = Seq((mod.modifierTypeId, mod.id))
      type M = BytesSerializable
      def serializer: Serializer[M] = ???
    }

    node ! DataFromPeer(dummySyncInfoMessageSpec, dummySyncInfo, peer)
    vhProbe.fishForMessage(3 seconds) { case m => m == OtherNodeSyncingInfo(peer, dummySyncInfo) }
  }

  property("NodeViewSynchronizer: OtherNodeSyncingStatus") { ctx =>
    import ctx._

  }

  property("NodeViewSynchronizer: DataFromPeer: InvSpec") { ctx =>
    import ctx._
    val spec = new InvSpec(3)
    val modifiers = Seq(mod.id)
    node ! DataFromPeer(spec, (mod.modifierTypeId, modifiers), peer)
    vhProbe.fishForMessage(3 seconds) { case m => m == CompareViews(peer, mod.modifierTypeId, modifiers) }
  }

  property("NodeViewSynchronizer: DataFromPeer: RequestModifierSpec") { ctx =>
    import ctx._
    val spec = new RequestModifierSpec(3)
    val modifiers = Seq(mod.id)
    node ! DataFromPeer(spec, (mod.modifierTypeId, modifiers), peer)
    vhProbe.fishForMessage(3 seconds) { case m => m == GetLocalObjects(peer, mod.modifierTypeId, modifiers) }
  }

  property("NodeViewSynchronizer: DataFromPeer: ModifiersSpec") { ctx =>
    import ctx._
    // todo
//    val spec = ModifiersSpec // fixme
//    val modifiers = Seq(mod.id)
//    node ! DataFromPeer(spec, (mod.modifierTypeId, modifiers), peer) // fixme
//    vhProbe.fishForMessage(3 seconds) { case m => m == ModifiersFromRemote(peer, mod.modifierTypeId, modifiers) }
  }

  property("NodeViewSynchronizer: RequestFromLocal") { ctx =>
    import ctx._
    node ! RequestFromLocal(peer, mod.modifierTypeId, Seq(mod.id))
    pchProbe.expectMsgType[Message[_]]
  }

  property("NodeViewSynchronizer: ResponseFromLocal") { ctx =>
    import ctx._
    node ! ResponseFromLocal(peer, mod.modifierTypeId, Seq(mod))
    pchProbe.expectMsgType[Message[_]]
  }


  // todo: check that source is added to `added`

}
