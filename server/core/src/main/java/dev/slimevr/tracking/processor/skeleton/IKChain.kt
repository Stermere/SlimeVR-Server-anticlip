package dev.slimevr.tracking.processor.skeleton

import dev.slimevr.tracking.processor.Bone
import dev.slimevr.tracking.processor.Constraint.Companion.ConstraintType
import dev.slimevr.tracking.trackers.Tracker
import io.github.axisangles.ktmath.Quaternion
import io.github.axisangles.ktmath.Vector3
import kotlin.math.*

/*
 * This class implements a chain of Bones
 */

class IKChain(
	val bones: MutableList<Bone>,
	var parent: IKChain?,
	val level: Int,
	val baseConstraint: Tracker?,
	val tailConstraint: Tracker?,
) {
	// State variables
	private val computedBasePosition = baseConstraint?.let { IKConstraint(it) }
	private val computedTailPosition = tailConstraint?.let { IKConstraint(it) }
	var children = mutableListOf<IKChain>()
	var target = Vector3.NULL
	var distToTargetSqr = Float.POSITIVE_INFINITY
	private var rotations = getRotationsList()

	private fun getRotationsList(): MutableList<Quaternion> {
		val rotList = mutableListOf<Quaternion>()
		for (b in bones) {
			rotList.add(b.getGlobalRotation())
		}

		return rotList
	}

	/*
	 * Populate the non-static rotations with the solve angle from the last iteration
	 */
	private fun prepBones() {
		for (i in 0..<bones.size) {
			if (bones[i].rotationConstraint.constraintType != ConstraintType.COMPLETE && bones[i].boneType.bodyPart !in IKSolver.LOCK_ROTATION) {
				bones[i].setRotationRaw(rotations[i])
			}
		}
	}

	fun backwardsCCDIK(useConstraints: Boolean) {
		target = computedTailPosition?.getPosition() ?: getChildTargetAvg()

		for (i in bones.size - 1 downTo 0) {
			val currentBone = bones[i]

			if (currentBone.boneType.bodyPart in IKSolver.LOCK_ROTATION) {
				rotations[i] = currentBone.getGlobalRotation()
				continue
			}

			// Get the local position of the end effector and the target relative to the current node
			val endEffectorLocal = (getEndEffectorsAvg() - currentBone.getPosition()).unit()
			val targetLocal = (target - currentBone.getPosition()).unit()

			// Compute the axis of rotation and angle for this bone
			val scalar = IKSolver.DAMPENING_FACTOR * if (currentBone.rotationConstraint.allowModifications) 1f else IKSolver.STATIC_DAMPENING
			var adjustment = Quaternion.fromTo(endEffectorLocal, targetLocal).pow(scalar).unit()

			// Bones that are not supposed to be modified should tend towards their origin
			val rotation = currentBone.getGlobalRotation()
			if (!currentBone.rotationConstraint.allowModifications) {
				adjustment *= rotation.interpR(currentBone.rotationConstraint.initialRotation, IKSolver.CORRECTION_FACTOR) * rotation.inv()
			}

			val correctedRot = (adjustment * rotation).unit()

			rotations[i] = setBoneRotation(currentBone, correctedRot, useConstraints)
		}
	}

	private fun getEndEffectorsAvg(): Vector3 {
		if (children.size < 1 || computedTailPosition != null) return bones.last().getTailPosition()

		var sum = Vector3.NULL
		for (c in children) {
			sum += c.getEndEffectorsAvg()
		}

		return sum / children.size.toFloat()
	}

	private fun getChildTargetAvg(): Vector3 {
		if (computedTailPosition != null) return computedTailPosition.getPosition()

		var sum = Vector3.NULL
		for (c in children) {
			sum += c.getChildTargetAvg()
		}

		return sum / children.size.toFloat()
	}

	/**
	 * Resets the chain to its default state
	 */
	fun resetChain() {
		distToTargetSqr = Float.POSITIVE_INFINITY

		for (b in bones) {
			b.rotationConstraint.initialRotation = b.getGlobalRotation()
		}
		prepBones()

		for (child in children) {
			child.resetChain()
		}
	}

	fun resetTrackerOffsets() {
		computedTailPosition?.reset(bones.last().getTailPosition())
		computedBasePosition?.reset(bones.first().getPosition())
	}

	/**
	 * Updates the distance to target and other fields
	 * Call on the root chain
	 */
	fun computeTargetDistance() {
		distToTargetSqr = if (computedTailPosition != null) {
			(bones.last().getTailPosition() - computedTailPosition.getPosition()).lenSq()
		} else {
			0.0f
		}

		for (chain in children) {
			chain.computeTargetDistance()
		}
	}

	/**
	 * Sets a bones rotation after constraining the rotation
	 * to the bone's rotational constraint
	 * returns the constrained rotation
	 */
	private fun setBoneRotation(bone: Bone, rotation: Quaternion, useConstraints: Boolean): Quaternion {
		// Constrain relative to the parent
		val newRotation = if (bone.rotationConstraint.constraintType == ConstraintType.COMPLETE) {
			bone.rotationConstraint.applyConstraint(rotation, bone)
		} else if (useConstraints && !bone.rotationConstraint.hasTrackerRotation) {
			bone.rotationConstraint.applyConstraint(rotation, bone)
		} else {
			rotation
		}

		bone.setRotationRaw(newRotation)
		bone.update()

		return newRotation
	}
}
