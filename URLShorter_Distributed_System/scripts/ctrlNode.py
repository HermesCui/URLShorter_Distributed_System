#!/usr/bin/env python3
import argparse
import subprocess
import paramiko
import time
import socket

# Default host list
configPath =  "../config/seed.data"

hostList = []
with open(configPath, 'r') as file:
    for line in file:
        data = line.strip().split(" ")
        hostList.append(data[0])

applicationNames = ["LoadBalancerServer", "ServerNodeAgent", "URLShortner", "TestLib"]
applicationFolderNames = ["proxyServer", "serverAgent", "serverSqlite", "testlibs"]

tooling = ["LoadTest"]

# Utility function to parse host list from CLI
def get_hosts(cli_hosts):
    if cli_hosts:
        return cli_hosts.split(',')
    else:
        return hostList

# Kills all instances started.
def killAll(hosts):
    for host in hosts:
        print("Creating session on {}".format(host))
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())  # Automatically add unknown hosts
        client.connect(hostname=host)
        for app in applicationNames + tooling:
            print("Killing process {} on host {}".format(app, host))
            CMD = f"jps | grep -e '{app}' | cut -d ' ' -f 1 | xargs -r kill"
            stdin, stdout, stderr = client.exec_command(CMD)
            exit_status = stdout.channel.recv_exit_status() 
        client.close()

from pathlib import Path
def launch_all(hosts):
    app_path = str(Path("../apps/serverAgent").resolve())
    print(app_path) 
    print(f"cd {app_path} && make agentRun &")
    for host in hosts:
        print("Creating session on {}".format(host))
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        client.connect(hostname=host)

        print("Launching agent {}".format(host))
        CMD = f"cd {app_path} && make agentRun"
        stdin, stdout, stderr = client.exec_command(CMD)
        print("Agent has been launched on {}".format(host))
        client.close()

def compile_all():
    app_path = "../apps"
    tk = []
    for x in applicationFolderNames:
        data_path = app_path + "/" + x
        z = subprocess.Popen(["make", "build"], cwd=data_path)
        tk.append(z)
        z.wait()
    
    for item in tk:
        item.wait()
    return

def reload(hosts):
     killAll(hosts)
     time.sleep(5)
     compile_all()
     time.sleep(5)
     launch_all(hosts)

def memoryWipe(hosts):
    time.sleep(5)
    for host in hosts:
        print("Creating session on {}".format(host))
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        client.connect(hostname=host)
        print("Formatting virtual memory on host {}".format(host))
        CMD = "bash -c 'rm -rf /virtual/$USER/URLShortner/'"
        stdin, stdout, stderr = client.exec_command(CMD)
        CMD2 = "bash -c 'install /virtual/$USER/URLShortner/'"
        stdin, stdout, stderr = client.exec_command(CMD2)
        print("Memory has been formatted on host {}".format(host))
        client.close()

# Kills all on the current host calling the script
def killLocal():
    host = socket.gethostname()
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())  # Automatically add unknown hosts
    client.connect(hostname=host)
    for app in applicationNames:
        print("Killing process {} on host {}".format(app, host))
        CMD = f"jps | grep -e '{app}' | cut -d ' ' -f 1 | xargs -r kill"
        stdin, stdout, stderr = client.exec_command(CMD)
        exit_status = stdout.channel.recv_exit_status() 
    client.close()

def initCluster(hosts):
     killAll(hosts)
     time.sleep(5)
     compile_all()
     time.sleep(5)
     memoryWipe(hosts)
     time.sleep(10)
     launch_all(hosts)

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Manage application deployment.")
    parser.add_argument(
        "-la", 
        "--launch_all", 
        action="store_true", 
        help="Launch all applications."
    )
    parser.add_argument(
        "-k", 
        "--kill", 
        action="store_true", 
        help="Kill all running instances of applications."
    )
    parser.add_argument(
        "-c", 
        "--compile", 
        action="store_true", 
        help="Compile all applications."
    )
    parser.add_argument(
        "-r", 
        "--reload", 
        action="store_true", 
        help="Reload all applications (kill, compile, and launch)."
    )
    parser.add_argument(
        "-mw", 
        "--memory_wipe", 
        action="store_true", 
        help="Wipe virtual memory on all hosts."
    )
    parser.add_argument(
        "-hosts", 
        "--hosts", 
        help="Comma-separated list of hosts to operate on. Defaults to all hosts in script."
    )

    args = parser.parse_args()

    # Parse hosts
    hosts = get_hosts(args.hosts)

    # Run the corresponding action
    if args.launch_all:
        launch_all(hosts)
    elif args.kill:
        killAll(hosts)
    elif args.compile:
        compile_all()
    elif args.reload:
        reload(hosts)
    elif args.memory_wipe:
        memoryWipe(hosts)
    else:
        print("No valid action provided. Use -h for help.")
