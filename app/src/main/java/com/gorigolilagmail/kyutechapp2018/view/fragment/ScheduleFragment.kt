package com.gorigolilagmail.kyutechapp2018.view.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.*
import android.widget.GridLayout
import android.widget.Toast

import com.gorigolilagmail.kyutechapp2018.R
import com.gorigolilagmail.kyutechapp2018.client.LoginClient
import com.gorigolilagmail.kyutechapp2018.client.RetrofitServiceGenerator.createService
import com.gorigolilagmail.kyutechapp2018.model.ApiRequest
import com.gorigolilagmail.kyutechapp2018.model.UserSchedule
import com.gorigolilagmail.kyutechapp2018.view.activity.UserScheduleDetailActivity
import com.gorigolilagmail.kyutechapp2018.view.customView.UserScheduleGridItem
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_schedule.*

class ScheduleFragment : MvpAppCompatFragment() {

    enum class Quarter(val id: Int) {
        FIRST_QUARTER(0),
        SECOND_QUARTER(1),
        THIRD_QUARTER(2),
        FOURTH_QUARTER(3)
    }

    var isEditing: Boolean = false

    var currentQuarter: Quarter = Quarter.FIRST_QUARTER

    private val userId: Int = LoginClient.getCurrentUserInfo()?.id ?: throw NullPointerException()
    private val userDepartment: String = LoginClient.getCurrentUserInfo()?.getDepartmentName()?: throw NullPointerException()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_schedule, container, false)
    }

    override fun onResume() {
        super.onResume()
        // クラスを全25コマ入れていく
        setScheduleItems( userId, currentQuarter.id)
    }

    fun setScheduleItems(userId: Int, quarter: Int, isEditing: Boolean = false) {

        Log.d("クオーターのエラー", "$quarter: ${Quarter.values().filter{it.id == quarter}.first()}")
        currentQuarter = Quarter.values().filter{it.id == quarter}.first()
        Log.d("isEditing", "$isEditing")
        (schedule_container as ViewGroup).removeAllViews() // すべての子Viewをまず消す
        // まず空のスケジュールを入れていく
        for(i in 0 until 5) {
            for( j in 0 until 5) {
                setScheduleItem(UserSchedule.createDummy(i, j, 0), isBlank = true, isEditing=isEditing)
            }
        }

        // スケジュール情報を取得してデータをsetしていく
        Log.d("QuarterItem", "userId:$userId, 現在第${currentQuarter.id + 1} クオーターです")
        createService().listUserScheduleByQuarter(userId, currentQuarter.id)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object: Observer<ApiRequest<UserSchedule>> {
                    override fun onComplete() {
                        try {
                            Log.d("onComplete", "UserScheduleComplete")
                            schedule_progress.visibility = View.GONE
                        } catch(e: IllegalStateException) {
                            Log.w("IllegalStateException!!", "${e.message}")
                        }
                    }

                    override fun onError(e: Throwable) {
                        Log.d("onError", "userSchedule OnError ${e.message}")
                        schedule_progress.visibility = View.GONE

                    }

                    override fun onNext(apiRequest: ApiRequest<UserSchedule>) {
                        val userSchedules: List<UserSchedule> = apiRequest.results
                        try {
                            userSchedules.forEach { userSchedule ->
                                setScheduleItem(userSchedule, isEditing = isEditing)
                            }
                        } catch(e: Exception) {
                            when(e) {
                                is NullPointerException, is java.lang.NullPointerException -> {
                                    Log.w("ぬるぽ", "${e.message}")
                                }
                                is IllegalStateException -> {
                                    Log.w("イリーガル", "${e.message}")
                                }
                            }
                        }
                    }

                    override fun onSubscribe(d: Disposable) {
                        schedule_progress.visibility = View.VISIBLE
                        Log.d("onSubscribe", "UserSchedule onSubscribe")
                    }
                })

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if(requestCode == RESULT_DIALOG_CODE) {
            val resultCode = data.getBooleanExtra("isSubmitted", false)
            if(resultCode) {
                Toast.makeText(context, "時間割の更新が完了しました!!", Toast.LENGTH_SHORT).show()
                setScheduleItems(userId, currentQuarter.id, isEditing=true)
            }
        }
    }

    private fun setScheduleItem(userSchedule: UserSchedule, isBlank: Boolean =false, isEditing: Boolean=false) {
        val item = UserScheduleGridItem(context, item = userSchedule, userDepartment=userDepartment)
        item.layoutParams = GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(userSchedule.day, GridLayout.FILL, 1f)
            rowSpec = GridLayout.spec(userSchedule.period, GridLayout.FILL, 1f)
        }

        if(isEditing) {
            item.setOnClickListener {
                Toast.makeText(context, "(${userSchedule.day}, ${userSchedule.period})" +
                        "のアイテムがタップされました", Toast.LENGTH_SHORT).show()
                userSchedule.run {
                    showSyllabusListDialog(period, day, quarter, userSchedule.id, userDepartment)
                }
            }
        } else { // 編集中じゃない -> 閲覧中の時
            if(isBlank.not()) {
                item.setOnClickListener {
                    UserScheduleDetailActivity.intent(context, userSchedule).run { startActivity(this) }
                }
            }
        }
        if(isBlank) {// BlankフラグがTrueだった場合
            item.setBlankSchedule()
        }
        schedule_container.addView(item)
    }


    private fun showSyllabusListDialog(period: Int, day: Int, quarter: Int, currentUserScheduleId: Int, userDepartment: String) {
        val dialog = SyllabusListDialogFragment.newInstance(period, day, quarter, currentUserScheduleId, userDepartment)
        dialog.setTargetFragment(this, RESULT_DIALOG_CODE)
        dialog.show(fragmentManager, "fragment_dialog")
    }

    companion object {
        private const val RESULT_DIALOG_CODE: Int = 100

        fun newInstance(page: Int): ScheduleFragment {
            val args: Bundle = Bundle().apply {
                putInt("page", page)
            }
            return ScheduleFragment().apply {
                arguments = args
            }
        }
    }




}// Required empty public constructor
