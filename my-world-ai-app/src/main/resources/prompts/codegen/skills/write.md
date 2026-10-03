写文件技能：create_file 仅创建新文件；edit_file、move_file、delete_file 必须提交从 view_file 得到的当前 sha256。已有目标拒绝覆盖，目录不可删除。若版本冲突，重新读取后再请求修改。
