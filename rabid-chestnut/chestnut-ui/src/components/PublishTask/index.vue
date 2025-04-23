<template>
  <!-- 添加调试模式，在开发时始终显示组件 -->
  <el-dropdown trigger="click" @command="handleCommand">
    <div style="font-size: 12px">
      <el-tag :type="taskCount > 0 ? 'success' : 'info'">
        {{ $t('Component.Layout.Navbar.PublishTask') }} [ {{ taskCount }} ]
      </el-tag>
    </div>
    <el-dropdown-menu slot="dropdown">
      <el-dropdown-item command="clear">
        {{ $t('Component.Layout.Navbar.ClearPublishTask') }}
      </el-dropdown-item>
      <el-dropdown-item command="refresh">
        刷新任务数量
      </el-dropdown-item>
      <el-dropdown-item command="setFlag">
        设置发布标志
      </el-dropdown-item>
    </el-dropdown-menu>
  </el-dropdown>
</template>

<script>
import { getPublishTaskCount, clearPublishTask } from "@/api/contentcore/publish";

export default {
  name: "CMSPublishTask",
  data() {
    return {
      taskCount: 0,
      interval: undefined,
      taskZeroTimes: 0
    }
  },
  created() {
    // 初始化时立即获取一次任务数量
    this.loadPublishTaskCount();
    // 设置定时器检查发布标志
    setInterval(this.checkPublishFlag, 2000);
  },
  methods: {
    checkPublishFlag() {
      if (this.$cache.local.get('publish_flag') === 'true') {
        if (!this.interval) {
          this.interval = setInterval(this.loadPublishTaskCount, 5000);
          this.taskZeroTimes = 0;
        }
      } else {
        if (this.interval) {
          clearInterval(this.interval)
          this.interval = undefined;
        }
      }
    },
    loadPublishTaskCount() {
      getPublishTaskCount().then(res => {
        try {
          // 检查响应是否有效
          if (res && res.code === 200 && res.data !== undefined) {
            console.log("获取到任务数量:", res.data);
            this.taskCount = res.data;
            if (this.taskCount == 0) {
              this.taskZeroTimes++;
              if (this.taskZeroTimes == 3) {
                console.log("任务数量连续3次为0，清除发布标志");
                this.$cache.local.set('publish_flag', '')
              }
            } else {
              // 如果任务数量不为0，重置计数器
              this.taskZeroTimes = 0;
            }
          } else {
            console.warn("获取任务数量响应无效，使用默认值0:", res);
            // 出错时不增加taskZeroTimes计数
          }
        } catch (err) {
          console.warn("处理响应数据出错，使用默认值0:", err);
        }
      }).catch(error => {
        console.warn("获取任务数量出错，使用默认值0:", error);
        // 出错不影响标志状态，也不显示错误消息给用户
      })
    },
    handleCommand(command) {
      if (command === 'clear') {
        clearPublishTask().then(() => {
          this.loadPublishTaskCount();
        }).catch(err => {
          console.warn("清空任务队列出错:", err);
          // 出错时不提示用户
        })
      } else if (command === 'refresh') {
        // 刷新任务数量
        this.loadPublishTaskCount();
      } else if (command === 'setFlag') {
        // 设置发布标志
        this.$cache.local.set('publish_flag', 'true');
        this.checkPublishFlag();
      }
    }
  }
}
</script>
