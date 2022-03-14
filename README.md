# Socks5 Proxy for Android Devices to forward packets from wifi network to cellular network

## Use with redsocks (https://github.com/darkk/redsocks)

### IPTables Commands
```bash
sudo iptables -t nat -N REDSOCKS
sudo iptables -t nat -A REDSOCKS -d 0.0.0.0/8 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 10.0.0.0/8 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 127.0.0.0/8 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 169.254.0.0/16 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 172.16.0.0/12 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 192.168.0.0/16 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 224.0.0.0/4 -j RETURN
sudo iptables -t nat -A REDSOCKS -d 240.0.0.0/4 -j RETURN
sudo iptables -t nat -A REDSOCKS -p tcp -j REDIRECT --to-ports 12345
sudo iptables -t nat -A REDSOCKS -p udp -j REDIRECT --to-ports 10053
sudo iptables -t nat -A OUTPUT -p tcp -m owner --gid-owner socksified -j REDSOCKS
sudo iptables -t nat -A OUTPUT -p udp -m owner --gid-owner socksified -j REDSOCKS
```

## Use with tun2socks (https://github.com/xjasonlyu/tun2socks)

### 1. Create tun device
```bash
sudo ip tuntap add mode tun dev tun0
sudo ip link set dev tun0 up
sudo ip addr add 192.168.66.1 dev tun0
```
### 2. Route all traffic to tun device (delete current default route before)
```bash
sudo ip route add default dev tun0
```
### 3. Start tun2socks
```bash
./tun2socks-linux-amd64 -device tun://tun0 -proxy socks5://192.168.36.14:4441
```
