package me.kuma.flirdemo

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * ViewBinding Helper
 *
 * link https://blog.csdn.net/c10WTiybQ1Ye3/article/details/112690188
 *
 * Created on: 2021/7/5 12:16 下午
 * @author lty <a href="mailto:lty81372860@gmail.com">Contact me.</a>
 */

/**
 * Activity
 */
inline fun <reified VB : ViewBinding> Activity.inflate() = lazy {
  inflateBinding<VB>(layoutInflater).apply { setContentView(root) }
}

inline fun <reified VB : ViewBinding> Dialog.inflate() = lazy {
  inflateBinding<VB>(layoutInflater).apply { setContentView(root) }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified VB : ViewBinding> inflateBinding(layoutInflater: LayoutInflater) =
  VB::class.java.getMethod("inflate", LayoutInflater::class.java).invoke(null, layoutInflater) as VB


/**
 * Fragment
 */
inline fun <reified VB : ViewBinding> Fragment.bindView() =
  FragmentBindingDelegate(VB::class.java)

class FragmentBindingDelegate<VB : ViewBinding>(
  private val clazz: Class<VB>
) : ReadOnlyProperty<Fragment, VB> {

  private var isInitialized = false
  private var _binding: VB? = null
  private val binding: VB get() = _binding!!

  override fun getValue(thisRef: Fragment, property: KProperty<*>): VB {
    if (!isInitialized) {
      thisRef.viewLifecycleOwner.lifecycle.addObserver(object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroyView() {
          _binding = null
        }
      })
      try {
        _binding = clazz.getMethod("bind", View::class.java)
          .invoke(null, thisRef.requireView()) as VB
        isInitialized = true
      } catch (e: Exception) {
        e.cause?.printStackTrace()
        throw e
      }
    }
    return binding
  }
}

/**
 * RecyclerView Item
 */
class BindingViewHolder<VB : ViewBinding>(val binding: VB) : RecyclerView.ViewHolder(binding.root)

inline fun <reified T : ViewBinding> newBindingViewHolder(parent: ViewGroup): BindingViewHolder<T> {
  val method = T::class.java.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.java)
  try {
    val binding = method.invoke(null, LayoutInflater.from(parent.context), parent, false) as T
    return BindingViewHolder(binding)
  } catch (e: Exception) {
    e.cause?.printStackTrace()
    throw e
  }
}
